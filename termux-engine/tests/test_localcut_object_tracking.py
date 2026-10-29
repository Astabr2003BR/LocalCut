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


if __name__ == "__main__":
    unittest.main()
