from __future__ import annotations

import threading
from types import MethodType

from localcut_audio_extract import AudioExtractFfmpegExecutor
from localcut_creative_server import (
    LocalCutCreativeHandler,
    _ensure_creative_worker_lock,
    create_toolchain_server,
)
from localcut_engine import PROJECT_ID, _error_code, _response, _safe_int, _validate_descriptor
from localcut_outputs import GeneratedMediaSpool


def _validate_extract_audio_input(input_data: dict) -> dict:
    descriptor = _validate_descriptor(input_data)
    if not descriptor["mimeType"].startswith("video/"):
        raise ValueError("EXTRACT_AUDIO_REQUIRES_VIDEO")
    duration_us = _safe_int(
        descriptor.get("sourceDurationUs"),
        "EXTRACT_AUDIO_DURATION_INVALID",
        1,
        AudioExtractFfmpegExecutor.MAX_DURATION_US,
    )
    return {
        "mode": "EXTRACT_AUDIO",
        **descriptor,
        "sourceDurationUs": duration_us,
    }


def _execute_extract_audio_job(server, job_id: str, transfer: dict) -> None:
    store = server.job_store
    outputs = server.generated_media_spool
    executor = server.audio_extract_executor
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
                log={"operation": "EXTRACT_AUDIO", "status": "RUNNING"},
            )
            current = store.get_job(job_id)
            output_path = outputs.prepare_output(job_id, mime_type="audio/mp4")
            result = executor.extract(
                input_path=server.media_spool.path_for(job_id),
                output_path=output_path,
                duration_us=current["input"]["sourceDurationUs"],
            )
            current = store.get_job(job_id)
            if current["status"] == "CANCELLED":
                outputs.remove(job_id)
                return
            media = outputs.finalize(job_id, mime_type="audio/mp4")
            store.update_job(
                job_id,
                status="SUCCEEDED",
                progress=100,
                output={
                    "transformMode": "extract-audio-v1",
                    "transfer": {
                        "sizeBytes": transfer["sizeBytes"],
                        "sha256": transfer["sha256"],
                    },
                    "media": media,
                    "durationUs": result.duration_us,
                },
                log={"operation": "EXTRACT_AUDIO", "status": "SUCCEEDED", "exitCode": 0},
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
                    "code": _error_code(error, "EXTRACT_AUDIO_FAILED"),
                    "message": "Audio extraction failed",
                },
                log={"operation": "EXTRACT_AUDIO", "status": "FAILED"},
            )


class LocalCutAudioCreativeHandler(LocalCutCreativeHandler):
    def _audio_extract_executor(self):
        executor = getattr(self.server, "audio_extract_executor", None)
        if executor is None or not bool(getattr(executor, "available", False)):
            raise RuntimeError("FFMPEG_UNAVAILABLE")
        return executor

    def _handle_get_job(self, request_id: str, request: dict) -> None:
        if request.get("includeGeneratedMedia") is not True:
            super()._handle_get_job(request_id, request)
            return

        store = self._require_jobs()
        job = store.get_job(request.get("jobId"))
        if job["type"] != "ANALYZE_MEDIA" or job["input"].get("mode") != "EXTRACT_AUDIO":
            super()._handle_get_job(request_id, request)
            return
        if job["status"] != "SUCCEEDED":
            raise ValueError("GENERATED_MEDIA_NOT_READY")

        outputs = self._generated_media_spool()
        media = outputs.metadata(job["id"])
        if media.get("mimeType") != "audio/mp4":
            raise ValueError("GENERATED_MEDIA_METADATA_MISMATCH")
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
        if (
            request.get("jobType") == "ANALYZE_MEDIA"
            and isinstance(input_data, dict)
            and input_data.get("mode") == "EXTRACT_AUDIO"
        ):
            self._audio_extract_executor()
            self._generated_media_spool()
            normalized_input = _validate_extract_audio_input(input_data)
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
        job = store.get_job(request.get("jobId"))
        if job["type"] == "ANALYZE_MEDIA" and job["input"].get("mode") == "EXTRACT_AUDIO":
            self._handle_stage_extract_audio(request_id, request, job)
            return
        super()._handle_stage_media(request_id, request)

    def _handle_stage_extract_audio(self, request_id: str, request: dict, job: dict) -> None:
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
            log={
                "operation": "STAGE_MEDIA",
                "status": "SUCCEEDED",
                "bytes": transfer["sizeBytes"],
            },
        )
        worker = threading.Thread(
            target=_execute_extract_audio_job,
            args=(self.server, job["id"], transfer),
            name=f"localcut-audio-extract-{job['id'][:8]}",
            daemon=True,
        )
        worker.start()
        public_transfer = {
            "sizeBytes": transfer["sizeBytes"],
            "sha256": transfer["sha256"],
        }
        self.wfile.write(_response(True, request_id, job=running, transfer=public_transfer))


def attach_audio_extract_support(server, audio_extract_executor, generated_media_spool=None):
    if generated_media_spool is not None:
        server.generated_media_spool = generated_media_spool
    server.audio_extract_executor = audio_extract_executor
    _ensure_creative_worker_lock(server)
    server.RequestHandlerClass = LocalCutAudioCreativeHandler
    base_capabilities = server.capabilities

    def capabilities(_self) -> list[str]:
        result = list(base_capabilities())
        if (
            audio_extract_executor is not None
            and bool(getattr(audio_extract_executor, "available", False))
            and getattr(server, "generated_media_spool", None) is not None
        ):
            for capability in ("EXTRACT_AUDIO", "DOWNLOAD_GENERATED_MEDIA"):
                if capability not in result:
                    result.append(capability)
        return result

    server.capabilities = MethodType(capabilities, server)
    return server


def create_audio_toolchain_server(
    *args,
    audio_extract_executor=None,
    generated_media_spool=None,
    **kwargs,
):
    server = create_toolchain_server(
        *args,
        generated_media_spool=generated_media_spool,
        **kwargs,
    )
    if audio_extract_executor is None and "FFMPEG" in server.capabilities():
        audio_extract_executor = AudioExtractFfmpegExecutor()

    outputs = getattr(server, "generated_media_spool", None)
    media_spool = kwargs.get("media_spool")
    if outputs is None and audio_extract_executor is not None and media_spool is not None:
        outputs = GeneratedMediaSpool(media_spool.root.parent / "outputs")
    return attach_audio_extract_support(server, audio_extract_executor, outputs)
