#!/usr/bin/env python3
from __future__ import annotations

import importlib
import importlib.util
import math
from dataclasses import dataclass
from pathlib import Path
from statistics import median


@dataclass(frozen=True)
class NormalizedTrackingRoi:
    x: float
    y: float
    width: float
    height: float

    def __post_init__(self) -> None:
        values = (self.x, self.y, self.width, self.height)
        if any(not math.isfinite(float(value)) for value in values):
            raise ValueError("OBJECT_TRACKING_ROI_INVALID")
        if (
            self.x < 0.0
            or self.y < 0.0
            or self.width <= 0.0
            or self.height <= 0.0
            or self.x + self.width > 1.0
            or self.y + self.height > 1.0
        ):
            raise ValueError("OBJECT_TRACKING_ROI_INVALID")
        if self.width < 0.01 or self.height < 0.01:
            raise ValueError("OBJECT_TRACKING_ROI_TOO_SMALL")


@dataclass(frozen=True)
class ObjectTrackingOptions:
    max_frames: int = 300
    sample_every: int = 1
    max_corners: int = 100

    def __post_init__(self) -> None:
        if not (2 <= int(self.max_frames) <= 600):
            raise ValueError("OBJECT_TRACKING_MAX_FRAMES_INVALID")
        if not (1 <= int(self.sample_every) <= 30):
            raise ValueError("OBJECT_TRACKING_SAMPLE_EVERY_INVALID")
        if not (20 <= int(self.max_corners) <= 500):
            raise ValueError("OBJECT_TRACKING_MAX_CORNERS_INVALID")


class OpenCvObjectPositionTracker:
    MIN_TRACKED_POINTS = 4

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

    @staticmethod
    def median_translation(previous_good, next_good) -> tuple[float, float]:
        try:
            previous = [(float(point[0]), float(point[1])) for point in previous_good]
            following = [(float(point[0]), float(point[1])) for point in next_good]
        except (IndexError, TypeError, ValueError) as exc:
            raise ValueError("OBJECT_TRACKING_POINTS_INVALID") from exc
        if len(previous) != len(following) or len(previous) < OpenCvObjectPositionTracker.MIN_TRACKED_POINTS:
            raise ValueError("OBJECT_TRACKING_POINTS_INVALID")
        dx = float(median(right[0] - left[0] for left, right in zip(previous, following)))
        dy = float(median(right[1] - left[1] for left, right in zip(previous, following)))
        if not math.isfinite(dx) or not math.isfinite(dy):
            raise ValueError("OBJECT_TRACKING_DELTA_INVALID")
        return dx, dy

    @staticmethod
    def clamp_roi_pixels(roi, frame_width: int, frame_height: int) -> list[float]:
        if frame_width <= 0 or frame_height <= 0:
            raise ValueError("OBJECT_TRACKING_FRAME_SIZE_INVALID")
        if len(roi) != 4:
            raise ValueError("OBJECT_TRACKING_ROI_INVALID")
        x, y, width, height = (float(value) for value in roi)
        if any(not math.isfinite(value) for value in (x, y, width, height)):
            raise ValueError("OBJECT_TRACKING_ROI_INVALID")
        width = max(2.0, min(width, float(frame_width)))
        height = max(2.0, min(height, float(frame_height)))
        x = max(0.0, min(x, float(frame_width) - width))
        y = max(0.0, min(y, float(frame_height) - height))
        return [x, y, width, height]

    def analyze(
        self,
        path: str | Path,
        roi: NormalizedTrackingRoi,
        options: ObjectTrackingOptions | None = None,
    ) -> dict:
        media_path = Path(path)
        if not media_path.is_file():
            raise FileNotFoundError("MEDIA_NOT_STAGED")
        options = options or ObjectTrackingOptions()
        cv2, np = self._backend()
        capture = cv2.VideoCapture(str(media_path))
        try:
            if not capture.isOpened():
                raise RuntimeError("OBJECT_TRACKING_VIDEO_OPEN_FAILED")
            frame_width = int(round(float(capture.get(cv2.CAP_PROP_FRAME_WIDTH))))
            frame_height = int(round(float(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))))
            if frame_width <= 0 or frame_height <= 0:
                raise RuntimeError("OBJECT_TRACKING_FRAME_SIZE_INVALID")
            fps = float(capture.get(cv2.CAP_PROP_FPS))
            if not math.isfinite(fps) or fps <= 0.0:
                raise RuntimeError("OBJECT_TRACKING_FPS_UNAVAILABLE")

            ok, frame = capture.read()
            if not ok or frame is None:
                raise RuntimeError("OBJECT_TRACKING_DECODE_FAILED")
            previous_gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            roi_pixels = self.clamp_roi_pixels(
                [
                    roi.x * frame_width,
                    roi.y * frame_height,
                    roi.width * frame_width,
                    roi.height * frame_height,
                ],
                frame_width,
                frame_height,
            )
            previous_points = self._detect_features(cv2, np, previous_gray, roi_pixels, options.max_corners)
            if previous_points is None or len(previous_points) < self.MIN_TRACKED_POINTS:
                raise RuntimeError("OBJECT_TRACKING_FEATURES_INSUFFICIENT")

            decoded_frames = 1
            frame_index = 0
            cumulative_x = 0.0
            cumulative_y = 0.0
            valid_samples = 0
            samples: list[dict] = []

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
                    previous_points = self._detect_features(
                        cv2,
                        np,
                        previous_gray,
                        roi_pixels,
                        options.max_corners,
                    )

                confidence = 0.0
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
                            dx, dy = self.median_translation(previous_good, next_good)
                            confidence = max(0.0, min(1.0, float(len(previous_good)) / float(max(1, count))))
                            cumulative_x += dx
                            cumulative_y += dy
                            roi_pixels[0] += dx
                            roi_pixels[1] += dy
                            roi_pixels = self.clamp_roi_pixels(roi_pixels, frame_width, frame_height)
                            valid_samples += 1

                samples.append(
                    {
                        "frameIndex": frame_index,
                        "timeUs": int(round(frame_index * 1_000_000.0 / fps)),
                        "dxNorm": cumulative_x / float(frame_width),
                        "dyNorm": cumulative_y / float(frame_height),
                        "centerXNorm": (roi_pixels[0] + roi_pixels[2] / 2.0) / float(frame_width),
                        "centerYNorm": (roi_pixels[1] + roi_pixels[3] / 2.0) / float(frame_height),
                        "confidence": confidence,
                    }
                )
                previous_gray = current_gray
                previous_points = self._detect_features(
                    cv2,
                    np,
                    current_gray,
                    roi_pixels,
                    options.max_corners,
                )

            if valid_samples == 0:
                raise RuntimeError("OBJECT_TRACKING_NO_VALID_SAMPLES")
            return {
                "trackingMode": "opencv-object-position-v1",
                "framesDecoded": decoded_frames,
                "frameWidth": frame_width,
                "frameHeight": frame_height,
                "frameRate": fps,
                "samples": samples,
            }
        finally:
            capture.release()

    @staticmethod
    def _detect_features(cv2, np, gray, roi_pixels, max_corners: int):
        x, y, width, height = OpenCvObjectPositionTracker.clamp_roi_pixels(
            roi_pixels,
            int(gray.shape[1]),
            int(gray.shape[0]),
        )
        mask = np.zeros_like(gray)
        x0 = max(0, int(math.floor(x)))
        y0 = max(0, int(math.floor(y)))
        x1 = min(int(gray.shape[1]), int(math.ceil(x + width)))
        y1 = min(int(gray.shape[0]), int(math.ceil(y + height)))
        mask[y0:y1, x0:x1] = 255
        return cv2.goodFeaturesToTrack(
            gray,
            maxCorners=int(max_corners),
            qualityLevel=0.01,
            minDistance=6.0,
            blockSize=7,
            mask=mask,
        )
