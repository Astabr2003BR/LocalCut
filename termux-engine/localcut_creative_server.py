#!/usr/bin/env python3
from __future__ import annotations

import threading
from types import MethodType

from localcut_engine import (
    PROJECT_ID,
    LocalCutTermuxEngineHandler,
    _error_code,
    _response,
    _safe_int,
    _validate_descriptor,
)
from localcut_outputs import GeneratedMediaSpool
from localcut_reverse import ReverseChunkPlanner, ReverseFfmpegExecutor
from localcut_toolchain import TOOL_SPECS, detect_toolchain
from localcut_vision import OpenCvGlobalMotionAnalyzer, TrackingOptions


_ALLOWED_TOOLCHAIN_CAPABILITIES = frozenset(spec.capability for spec in TOOL_SPECS)


def validate_toolchain_capabilities(values) -> tuple[str, ...]:
    result: list[str] = []
    for raw in values:
        if not isinstance(raw, str) or raw not in _ALLOWED_TOOLCHAIN_CAPABILITIES:
            raise ValueError("TOOLCHAIN_CAPABILITY_INVALID")
        if raw not in result:
            result.append(raw)
    return tuple(result)


def _ensure_creative_worker_lock(server):
    lock = getattr(server, "creative_worker_lock", None)
    if lock is None:
        lock = threading.Lock()
        server.creative_worker_lock = lock
    return lock


def _validate_tracking_input(input_data: dict) -> dict:
    descriptor = _validate_descriptor(input_data)
    mime_type = descriptor["mimeType"]
    if not mime_type.startswith("video/"):
        raise ValueError("TRACKING_MEDIA_TYPE_INVALID")

    options = TrackingOptions(
        max_frames=_safe_int(
            input_data.get("maxFrames", TrackingOptions.max_frames),
            "TRACKING_MAX_FRAMES_INVALID",
            2,
            600,
        ),
        sample_every=_safe_int(
            input_data.get("sampleEvery", TrackingOptions.sample_every),
            "TRACKING_SAMPLE_EVERY_INVALID",
            1,
            30,
        ),
        max_corners=_safe_int(
            input_data.get("maxCorners", TrackingOptions.max_corners),
            "TRACKING_MAX_CORNERS_INVALID",
            20,
            500,
        ),
    )
    return {
        "mode": "TRACKING",
        **descriptor,
        "maxFrames": options.max_frames,
        "sampleEvery": options.sample_every,
        "maxCorners": options.max_corners,
    }


def _validate_reverse_input(input_data: dict) -> dict:
    descriptor = _validate_descriptor(input_data)
    if not descriptor["mimeType"].startswith("video/"):
        raise ValueError("REVERSE_MEDIA_TYPE_INVALID")
    duration_us = _safe_int(
        descriptor.get("sourceDurationUs"),
        "REVERSE_DURATION_INVALID",
        1,
        ReverseChunkPlanner.MAX_DURATION_US,
    )
    chunk_duration_us = _safe_int(
        input_data.get("chunkDurationUs", ReverseChunkPlanner.DEFAULT_CHUNK_DURATION_US),
        "REVERSE_CHUNK_DURATION_INVALID",
        ReverseChunkPlanner.MIN_CHUNK_DURATION_US,
        ReverseChunkPlanner.MAX_CHUNK_DURATION_US,
    )
    return {
        "mode": "REVERSE",
        **descriptor,
        "sourceDurationUs": duration_us,
        "chunkDurationUs": chunk_duration_us,
    }


def _execute_tracking_job(server, job_id: str, transfer: dict) -> None:
    store = server.job_store
    analyzer = server.tracking_analyzer
    lock = _ensure_creative_worker_lock(server)
    if store is None or server.media_spool is None or analyzer is None:
        return

    with lock:
        try:
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                return
            if current["status"] != "RUNNING":
                return
            store.update_job(
                job_id,
                status="RUNNING",
                progress=35,
                log={"operation": "ANALYZE_TRACKING", "status": "RUNNING"},
            )
            current = store.get_job(job_id)
            options = TrackingOptions(
                max_frames=current["input"]["maxFrames"],
                sample_every=current["input"]["sampleEvery"],
                max_corners=current["input"]["maxCorners"],
            )
            analysis = analyzer.analyze(server.media_spool.path_for(job_id), options=options)
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                return
            store.update_job(
                job_id,
                status="SUCCEEDED",
                progress=100,
                output={
                    "analysisMode": "opencv-global-motion-v1",
                    "transfer": {"sizeBytes": transfer["sizeBytes"], "sha256": transfer["sha256"]},
                    "analysis": analysis,
                },
                log={"operation": "ANALYZE_TRACKING", "status": "SUCCEEDED", "exitCode": 0},
            )
        except Exception as error:
            try:
                current = store.get_job(job_id)
            except Exception:
                return
            if current["status"] == "CANCELLED":
                return
            store.update_job(
                job_id,
                status="FAILED",
                progress=100,
                error={
                    "code": _error_code(error, "ANALYZE_TRACKING_FAILED"),
                    "message": "Tracking analysis failed",
                },
                log={"operation": "ANALYZE_TRACKING", "status": "FAILED"},
            )


def _execute_reverse_job(server, job_id: str, transfer: dict) -> None:
    store = server.job_store
    outputs = server.generated_media_spool
    executor = server.reverse_executor
    lock = _ensure_creative_worker_lock(server)
    if store is None or server.media_spool is None or outputs is None or executor is None:
        return

    with lock:
        try:
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                outputs.remove(job_id)
                return
            if current["status"] != "RUNNING":
                return
            store.update_job(
                job_id,
                status="RUNNING",
                progress=35,
                log={"operation": "REVERSE_MEDIA", "status": "RUNNING"},
            )
            current = store.get_job(job_id)
            output_path = outputs.prepare_output(job_id)
            result = executor.reverse(
                input_path=server.media_spool.path_for(job_id),
                output_path=output_path,
                duration_us=current["input"]["sourceDurationUs"],
                chunk_duration_us=current["input"]["chunkDurationUs"],
                include_audio=True,
            )
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                outputs.remove(job_id)
                return
            media = outputs.finalize(job_id)
            store.update_job(
                job_id,
                status="SUCCEEDED",
                progress=100,
                output={
                    "transformMode": "reverse-v1",
                    "transfer": {"sizeBytes": transfer["sizeBytes"], "sha256": transfer["sha256"]},
                    "media": media,
                    "chunkCount": result.chunk_count,
                },
                log={"operation": "REVERSE_MEDIA", "status": "SUCCEEDED", "exitCode": 0},
            )
        except Exception as error:
            outputs.remove(job_id)
            try:
                current = store.get_job(job_id)
            except Exception:
                return
            if current["status"] == "CANCELLED":
                return
            store.update_job(
                job_id,
                status="FAILED",
                progress=100,
                error={
                    "code": _error_code(error, "REVERSE_MEDIA_FAILED"),
                    "message": "Reverse media transform failed",
                },
                log={"operation": "REVERSE_MEDIA", "status": "FAILED"},
            )


class LocalCutCreativeHandler(LocalCutTermuxEngineHandler):
    def _tracking_analyzer(self):
        analyzer = getattr(self.server, "tracking_analyzer", None)
        if analyzer is None or not bool(getattr(analyzer, "available", False)):
            raise RuntimeError("OPENCV_UNAVAILABLE")
        return analyzer

    def _reverse_executor(self):
        executor = getattr(self.server, "reverse_executor", None)
        if executor is None or not bool(getattr(executor, "available", False)):
            raise RuntimeError("FFMPEG_UNAVAILABLE")
        return executor

    def _generated_media_spool(self):
        spool = getattr(self.server, "generated_media_spool", None)
        if spool is None:
            raise ValueError("OPERATION_NOT_ALLOWED")
        return spool

    def _handle_get_job(self, request_id: str, request: dict) -> None:
        if request.get("includeGeneratedMedia") is not True:
            super()._handle_get_job(request_id, request)
            return

        store = self._require_jobs()
        job = store.get_job(request.get("jobId"))
        if (
            job["status"] != "SUCCEEDED"
            or job["type"] != "ANALYZE_MEDIA"
            or job["input"].get("mode") != "REVERSE"
        ):
            raise ValueError("GENERATED_MEDIA_NOT_READY")

        outputs = self._generated_media_spool()
        media = outputs.metadata(job["id"])
        job_media = (job.get("output") or {}).get("media")
        if job_media != media:
            raise ValueError("GENERATED_MEDIA_METADATA_MISMATCH")

        self.wfile.write(
            _response(
                True,
                request_id,
                job=job,
                media=media,
                contentLength=media["sizeBytes"],
                sha256=media["sha256"],
                binaryBody=True,
            )
        )
        self.wfile.flush()
        for chunk in outputs.open_chunks(job["id"]):
            self.wfile.write(chunk)
        self.wfile.flush()

    def _handle_create_job(self, request_id: str, request: dict) -> None:
        input_data = request.get("input")
        if request.get("jobType") == "ANALYZE_MEDIA" and isinstance(input_data, dict):
            if input_data.get("mode") == "TRACKING":
                self._tracking_analyzer()
                normalized_input = _validate_tracking_input(input_data)
                job = self._require_jobs().create_job(
                    project_id=PROJECT_ID,
                    job_type="ANALYZE_MEDIA",
                    idempotency_key=request.get("idempotencyKey"),
                    input_data=normalized_input,
                )
                self.wfile.write(_response(True, request_id, job=job))
                return
            if input_data.get("mode") == "REVERSE":
                self._reverse_executor()
                self._generated_media_spool()
                normalized_input = _validate_reverse_input(input_data)
                job = self._require_jobs().create_job(
                    project_id=PROJECT_ID,
                    job_type="ANALYZE_MEDIA",
                    idempotency_key=request.get("idempotencyKey"),
                    input_data=normalized_input,
                )
                self.wfile.write(_response(True, request_id, job=job))
                return
        super()._handle_create_job(request_id, request)

    def _handle_stage_media(self, request_id: str, request: dict) -> None:
        store = self._require_jobs()
        job_id = request.get("jobId")
        job = store.get_job(job_id)
        if job["type"] == "ANALYZE_MEDIA" and job["input"].get("mode") == "REVERSE":
            self._handle_stage_reverse(request_id, request, job)
            return
        if job["type"] != "ANALYZE_MEDIA" or job["input"].get("mode") != "TRACKING":
            super()._handle_stage_media(request_id, request)
            return

        spool = self.server.media_spool
        if spool is None:
            raise ValueError("OPERATION_NOT_ALLOWED")
        if job["status"] != "QUEUED":
            raise ValueError("JOB_NOT_QUEUED")

        content_length = _safe_int(request.get("contentLength"), "MEDIA_LENGTH_INVALID", 1)
        declared_size = job["input"].get("sizeBytes")
        if declared_size is not None and int(declared_size) != content_length:
            raise ValueError("MEDIA_LENGTH_MISMATCH")

        transfer = spool.stage_bytes(job_id, content_length, self._body_chunks(content_length))
        running = store.update_job(
            job_id,
            status="RUNNING",
            progress=20,
            log={"operation": "STAGE_MEDIA", "status": "SUCCEEDED", "bytes": transfer["sizeBytes"]},
        )
        worker = threading.Thread(
            target=_execute_tracking_job,
            args=(self.server, job_id, transfer),
            name=f"localcut-tracking-{job_id[:8]}",
            daemon=True,
        )
        worker.start()
        public_transfer = {"sizeBytes": transfer["sizeBytes"], "sha256": transfer["sha256"]}
        self.wfile.write(_response(True, request_id, job=running, transfer=public_transfer))

    def _handle_stage_reverse(self, request_id: str, request: dict, job: dict) -> None:
        store = self._require_jobs()
        spool = self.server.media_spool
        if spool is None:
            raise ValueError("OPERATION_NOT_ALLOWED")
        if job["status"] != "QUEUED":
            raise ValueError("JOB_NOT_QUEUED")

        content_length = _safe_int(request.get("contentLength"), "MEDIA_LENGTH_INVALID", 1)
        declared_size = job["input"].get("sizeBytes")
        if declared_size is not None and int(declared_size) != content_length:
            raise ValueError("MEDIA_LENGTH_MISMATCH")

        transfer = spool.stage_bytes(job["id"], content_length, self._body_chunks(content_length))
        running = store.update_job(
            job["id"],
            status="RUNNING",
            progress=20,
            log={"operation": "STAGE_MEDIA", "status": "SUCCEEDED", "bytes": transfer["sizeBytes"]},
        )
        worker = threading.Thread(
            target=_execute_reverse_job,
            args=(self.server, job["id"], transfer),
            name=f"localcut-reverse-{job['id'][:8]}",
            daemon=True,
        )
        worker.start()
        public_transfer = {"sizeBytes": transfer["sizeBytes"], "sha256": transfer["sha256"]}
        self.wfile.write(_response(True, request_id, job=running, transfer=public_transfer))


def attach_toolchain_capabilities(server, capabilities):
    validated = validate_toolchain_capabilities(capabilities)
    base_capabilities = server.capabilities

    def capabilities(_self) -> list[str]:
        result = list(base_capabilities())
        for capability in validated:
            if capability not in result:
                result.append(capability)
        return result

    server.capabilities = MethodType(capabilities, server)
    return server


def attach_tracking_support(server, tracking_analyzer):
    server.tracking_analyzer = tracking_analyzer
    _ensure_creative_worker_lock(server)
    server.RequestHandlerClass = LocalCutCreativeHandler
    base_capabilities = server.capabilities

    def capabilities(_self) -> list[str]:
        result = list(base_capabilities())
        if tracking_analyzer is not None and bool(getattr(tracking_analyzer, "available", False)):
            for capability in ("ANALYZE_TRACKING", "OPENCV_GLOBAL_MOTION"):
                if capability not in result:
                    result.append(capability)
        return result

    server.capabilities = MethodType(capabilities, server)
    return server


def attach_reverse_support(server, reverse_executor, generated_media_spool):
    server.reverse_executor = reverse_executor
    server.generated_media_spool = generated_media_spool
    _ensure_creative_worker_lock(server)
    server.RequestHandlerClass = LocalCutCreativeHandler
    base_capabilities = server.capabilities

    def capabilities(_self) -> list[str]:
        result = list(base_capabilities())
        if (
            reverse_executor is not None
            and bool(getattr(reverse_executor, "available", False))
            and generated_media_spool is not None
        ):
            for capability in ("REVERSE_MEDIA", "DOWNLOAD_GENERATED_MEDIA"):
                if capability not in result:
                    result.append(capability)
        return result

    server.capabilities = MethodType(capabilities, server)
    return server


def create_toolchain_server(
    *args,
    toolchain_capabilities=None,
    tracking_analyzer=None,
    reverse_executor=None,
    generated_media_spool=None,
    **kwargs,
):
    if toolchain_capabilities is None:
        toolchain_capabilities = detect_toolchain()["capabilities"]
    validated = validate_toolchain_capabilities(toolchain_capabilities)

    from localcut_engine import create_server

    server = create_server(*args, **kwargs)
    server = attach_toolchain_capabilities(server, validated)
    if tracking_analyzer is None and "OPENCV" in validated:
        tracking_analyzer = OpenCvGlobalMotionAnalyzer()
    server = attach_tracking_support(server, tracking_analyzer)

    media_spool = kwargs.get("media_spool")
    if reverse_executor is None and "FFMPEG" in validated:
        reverse_executor = ReverseFfmpegExecutor()
    if generated_media_spool is None and reverse_executor is not None and media_spool is not None:
        generated_media_spool = GeneratedMediaSpool(media_spool.root.parent / "outputs")
    return attach_reverse_support(server, reverse_executor, generated_media_spool)
