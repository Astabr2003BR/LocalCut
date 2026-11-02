#!/usr/bin/env python3
from __future__ import annotations

import importlib
import importlib.util
import math
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class TrackingOptions:
    max_frames: int = 300
    sample_every: int = 2
    max_corners: int = 120

    def __post_init__(self) -> None:
        if not (2 <= int(self.max_frames) <= 600):
            raise ValueError("TRACKING_MAX_FRAMES_INVALID")
        if not (1 <= int(self.sample_every) <= 30):
            raise ValueError("TRACKING_SAMPLE_EVERY_INVALID")
        if not (20 <= int(self.max_corners) <= 500):
            raise ValueError("TRACKING_MAX_CORNERS_INVALID")


class OpenCvGlobalMotionAnalyzer:
    MIN_TRACKED_POINTS = 6

    def __init__(self, cv2_module=None, numpy_module=None):
        self._cv2 = cv2_module
        self._numpy = numpy_module

    @property
    def available(self) -> bool:
        if self._cv2 is not None and self._numpy is not None:
            return True
        try:
            return importlib.util.find_spec("cv2") is not None and importlib.util.find_spec("numpy") is not None
        except (ImportError, AttributeError, ValueError):
            return False

    def _backend(self):
        cv2 = self._cv2
        numpy = self._numpy
        if cv2 is None:
            try:
                cv2 = importlib.import_module("cv2")
            except (ImportError, OSError) as exc:
                raise RuntimeError("OPENCV_UNAVAILABLE") from exc
        if numpy is None:
            try:
                numpy = importlib.import_module("numpy")
            except (ImportError, OSError) as exc:
                raise RuntimeError("NUMPY_UNAVAILABLE") from exc
        return cv2, numpy

    def analyze(self, path: str | Path, options: TrackingOptions | None = None) -> dict:
        media_path = Path(path)
        if not media_path.is_file():
            raise FileNotFoundError("MEDIA_NOT_STAGED")
        options = options or TrackingOptions()
        cv2, np = self._backend()
        capture = cv2.VideoCapture(str(media_path))
        try:
            if not capture.isOpened():
                raise RuntimeError("TRACKING_VIDEO_OPEN_FAILED")
            fps = float(capture.get(cv2.CAP_PROP_FPS))
            if not math.isfinite(fps) or fps <= 0.0:
                fps = None

            ok, frame = capture.read()
            if not ok or frame is None:
                raise RuntimeError("TRACKING_DECODE_FAILED")
            previous_gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            previous_points = self._detect_features(cv2, previous_gray, options.max_corners)
            decoded_frames = 1
            frame_index = 0
            samples = []

            while decoded_frames < options.max_frames:
                ok, frame = capture.read()
                if not ok or frame is None:
                    break
                decoded_frames += 1
                frame_index += 1
                if frame_index % options.sample_every != 0:
                    continue

                current_gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                if previous_points is None or len(previous_points) < self.MIN_TRACKED_POINTS:
                    previous_points = self._detect_features(cv2, previous_gray, options.max_corners)
                if previous_points is not None and len(previous_points) >= self.MIN_TRACKED_POINTS:
                    next_points, status, _ = cv2.calcOpticalFlowPyrLK(
                        previous_gray,
                        current_gray,
                        previous_points,
                        None,
                        winSize=(21, 21),
                        maxLevel=3,
                        criteria=(
                            cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT,
                            30,
                            0.01,
                        ),
                    )
                    if next_points is not None and status is not None:
                        previous_flat = np.asarray(previous_points, dtype=np.float32).reshape(-1, 2)
                        next_flat = np.asarray(next_points, dtype=np.float32).reshape(-1, 2)
                        status_flat = np.asarray(status).reshape(-1)
                        count = min(len(previous_flat), len(next_flat), len(status_flat))
                        mask = status_flat[:count] == 1
                        previous_good = previous_flat[:count][mask]
                        next_good = next_flat[:count][mask]
                        if len(previous_good) >= self.MIN_TRACKED_POINTS:
                            matrix, inliers = cv2.estimateAffinePartial2D(
                                previous_good,
                                next_good,
                                method=cv2.RANSAC,
                                ransacReprojThreshold=3.0,
                                maxIters=1000,
                                confidence=0.99,
                                refineIters=10,
                            )
                            if matrix is not None:
                                confidence = self._inlier_confidence(inliers, len(previous_good), np)
                                motion = self.decompose_affine(matrix, confidence)
                                sample = {
                                    "frameIndex": frame_index,
                                    **motion,
                                }
                                if fps is not None:
                                    sample["timeUs"] = int(round(frame_index * 1_000_000.0 / fps))
                                samples.append(sample)

                previous_gray = current_gray
                previous_points = self._detect_features(cv2, current_gray, options.max_corners)

            return {
                "trackingMode": "opencv-global-motion-v1",
                "framesDecoded": decoded_frames,
                "sampleEvery": options.sample_every,
                "maxCorners": options.max_corners,
                "frameRate": fps,
                "samples": samples,
            }
        finally:
            capture.release()

    @staticmethod
    def _detect_features(cv2, gray, max_corners: int):
        return cv2.goodFeaturesToTrack(
            gray,
            maxCorners=int(max_corners),
            qualityLevel=0.01,
            minDistance=8.0,
            blockSize=7,
        )

    @staticmethod
    def _inlier_confidence(inliers, tracked_count: int, np) -> float:
        if tracked_count <= 0 or inliers is None:
            return 0.0
        flat = np.asarray(inliers).reshape(-1)
        if len(flat) == 0:
            return 0.0
        return max(0.0, min(1.0, float((flat != 0).sum()) / float(tracked_count)))

    @staticmethod
    def decompose_affine(matrix, confidence: float) -> dict:
        try:
            rows = [
                [float(matrix[0][0]), float(matrix[0][1]), float(matrix[0][2])],
                [float(matrix[1][0]), float(matrix[1][1]), float(matrix[1][2])],
            ]
        except (IndexError, KeyError, TypeError, ValueError) as exc:
            raise ValueError("TRACKING_AFFINE_INVALID") from exc
        values = [value for row in rows for value in row]
        if any(not math.isfinite(value) for value in values):
            raise ValueError("TRACKING_AFFINE_INVALID")
        confidence = float(confidence)
        if not math.isfinite(confidence) or not (0.0 <= confidence <= 1.0):
            raise ValueError("TRACKING_CONFIDENCE_INVALID")

        a = rows[0][0]
        b = rows[1][0]
        scale = math.hypot(a, b)
        if not math.isfinite(scale) or scale <= 0.0:
            raise ValueError("TRACKING_AFFINE_INVALID")
        return {
            "dx": rows[0][2],
            "dy": rows[1][2],
            "rotationDeg": math.degrees(math.atan2(b, a)),
            "scale": scale,
            "confidence": confidence,
        }
