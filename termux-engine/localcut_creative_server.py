#!/usr/bin/env python3
from __future__ import annotations

from types import MethodType

from localcut_engine import (
    PROJECT_ID,
    LocalCutTermuxEngineHandler,
    _error_code,
    _response,
    _safe_int,
    _validate_descriptor,
)
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


class LocalCutCreativeHandler(LocalCutTermuxEngineHandler):
    def _tracking_analyzer(self):
        analyzer = getattr(self.server, "tracking_analyzer", None)
        if analyzer is None or not bool(getattr(analyzer, "available", False)):
            raise RuntimeError("OPENCV_UNAVAILABLE")
        return analyzer

    def _handle_create_job(self, request_id: str, request: dict) -> None:
        input_data = request.get("input")
        if (
            request.get("jobType") == "ANALYZE_MEDIA"
            and isinstance(input_data, dict)
            and input_data.get("mode") == "TRACKING"
        ):
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
        super()._handle_create_job(request_id, request)

    def _handle_stage_media(self, request_id: str, request: dict) -> None:
        store = self._require_jobs()
        job_id = request.get("jobId")
        job = store.get_job(job_id)
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
        store.update_job(
            job_id,
            status="RUNNING",
            progress=35,
            log={"operation": "STAGE_MEDIA", "status": "SUCCEEDED", "bytes": transfer["sizeBytes"]},
        )
        job = self._execute_staged_tracking(job_id, transfer)
        public_transfer = {"sizeBytes": transfer["sizeBytes"], "sha256": transfer["sha256"]}
        self.wfile.write(_response(True, request_id, job=job, transfer=public_transfer))

    def _execute_staged_tracking(self, job_id: str, transfer: dict) -> dict:
        store = self._require_jobs()
        try:
            analyzer = self._tracking_analyzer()
            current = store.get_job(job_id)
            options = TrackingOptions(
                max_frames=current["input"]["maxFrames"],
                sample_every=current["input"]["sampleEvery"],
                max_corners=current["input"]["maxCorners"],
            )
            analysis = analyzer.analyze(self.server.media_spool.path_for(job_id), options=options)
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                return current
            return store.update_job(
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
        except (ValueError, KeyError, FileNotFoundError, RuntimeError) as error:
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                return current
            return store.update_job(
                job_id,
                status="FAILED",
                progress=100,
                error={
                    "code": _error_code(error, "ANALYZE_TRACKING_FAILED"),
                    "message": "Tracking analysis failed",
                },
                log={"operation": "ANALYZE_TRACKING", "status": "FAILED"},
            )


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


def create_toolchain_server(*args, toolchain_capabilities=None, tracking_analyzer=None, **kwargs):
    if toolchain_capabilities is None:
        toolchain_capabilities = detect_toolchain()["capabilities"]
    validated = validate_toolchain_capabilities(toolchain_capabilities)

    from localcut_engine import create_server

    server = create_server(*args, **kwargs)
    server = attach_toolchain_capabilities(server, validated)
    if tracking_analyzer is None and "OPENCV" in validated:
        tracking_analyzer = OpenCvGlobalMotionAnalyzer()
    return attach_tracking_support(server, tracking_analyzer)
