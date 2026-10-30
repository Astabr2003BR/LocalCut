import math
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_object_tracking import (
    NormalizedTrackingRoi,
    ObjectTrackingOptions,
    OpenCvObjectPositionTracker,
)


class _FakeCapture:
    def __init__(self):
        self.set_calls = []

    def set(self, prop, value):
        self.set_calls.append((prop, value))
        return True


class _FakeCv2Orientation:
    CAP_PROP_ORIENTATION_AUTO = 49


class _FakeFrame:
    shape = (1920, 1080, 3)


class ObjectTrackingCoreTest(unittest.TestCase):
    def test_roi_and_options_fail_closed_outside_mobile_bounds(self):
        roi = NormalizedTrackingRoi(0.1, 0.2, 0.3, 0.4)
        self.assertAlmostEqual(0.1, roi.x)
        self.assertEqual(300, ObjectTrackingOptions().max_frames)

        invalid_rois = (
            (-0.1, 0.0, 0.2, 0.2),
            (0.9, 0.0, 0.2, 0.2),
            (0.0, 0.0, 0.0, 0.2),
            (0.0, 0.0, 0.005, 0.2),
            (math.nan, 0.0, 0.2, 0.2),
        )
        for values in invalid_rois:
            with self.subTest(values=values):
                with self.assertRaises(ValueError):
                    NormalizedTrackingRoi(*values)

        with self.assertRaisesRegex(ValueError, "OBJECT_TRACKING_MAX_FRAMES_INVALID"):
            ObjectTrackingOptions(max_frames=1)
        with self.assertRaisesRegex(ValueError, "OBJECT_TRACKING_SAMPLE_EVERY_INVALID"):
            ObjectTrackingOptions(sample_every=31)
        with self.assertRaisesRegex(ValueError, "OBJECT_TRACKING_MAX_CORNERS_INVALID"):
            ObjectTrackingOptions(max_corners=10)

    def test_median_translation_rejects_outlier_and_invalid_points(self):
        previous = [(10.0, 10.0), (20.0, 15.0), (30.0, 25.0), (40.0, 35.0), (50.0, 45.0)]
        following = [(13.0, 8.0), (23.0, 13.0), (33.0, 23.0), (43.0, 33.0), (500.0, -900.0)]

        dx, dy = OpenCvObjectPositionTracker.median_translation(previous, following)

        self.assertAlmostEqual(3.0, dx)
        self.assertAlmostEqual(-2.0, dy)
        with self.assertRaisesRegex(ValueError, "OBJECT_TRACKING_POINTS_INVALID"):
            OpenCvObjectPositionTracker.median_translation(previous[:3], following[:3])
        with self.assertRaisesRegex(ValueError, "OBJECT_TRACKING_POINTS_INVALID"):
            OpenCvObjectPositionTracker.median_translation(previous, following[:4])

    def test_roi_clamp_never_escapes_frame(self):
        self.assertEqual(
            [80.0, 70.0, 20.0, 30.0],
            OpenCvObjectPositionTracker.clamp_roi_pixels([95.0, 90.0, 20.0, 30.0], 100, 100),
        )
        self.assertEqual(
            [0.0, 0.0, 100.0, 100.0],
            OpenCvObjectPositionTracker.clamp_roi_pixels([-10.0, -20.0, 500.0, 500.0], 100, 100),
        )

    def test_frame_budget_is_limited_to_selected_clip_source_range(self):
        self.assertEqual(
            61,
            OpenCvObjectPositionTracker.frame_budget(
                fps=30.0,
                source_in_us=2_000_000,
                source_out_us=4_000_000,
                max_frames=300,
            ),
        )
        self.assertEqual(
            120,
            OpenCvObjectPositionTracker.frame_budget(
                fps=30.0,
                source_in_us=2_000_000,
                source_out_us=None,
                max_frames=120,
            ),
        )
        with self.assertRaisesRegex(ValueError, "OBJECT_TRACKING_SOURCE_RANGE_INVALID"):
            OpenCvObjectPositionTracker.frame_budget(30.0, 4_000_000, 2_000_000, 300)
        with self.assertRaisesRegex(ValueError, "OBJECT_TRACKING_SOURCE_RANGE_TOO_SHORT"):
            OpenCvObjectPositionTracker.frame_budget(30.0, 0, 1_000, 300)

    def test_orientation_is_explicit_and_frame_geometry_comes_from_decoded_pixels(self):
        capture = _FakeCapture()
        enabled = OpenCvObjectPositionTracker.enable_orientation_auto(capture, _FakeCv2Orientation)

        self.assertTrue(enabled)
        self.assertEqual([(49, 1.0)], capture.set_calls)
        self.assertEqual(
            (1080, 1920),
            OpenCvObjectPositionTracker.decoded_frame_size(_FakeFrame()),
        )

    def test_decoded_frame_size_rejects_invalid_shape(self):
        class BadFrame:
            shape = (0, 1920, 3)

        with self.assertRaisesRegex(ValueError, "OBJECT_TRACKING_FRAME_SIZE_INVALID"):
            OpenCvObjectPositionTracker.decoded_frame_size(BadFrame())


if __name__ == "__main__":
    unittest.main()
