import math
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_vision import OpenCvGlobalMotionAnalyzer, TrackingOptions


class OpenCvGlobalMotionAnalyzerTest(unittest.TestCase):
    def test_options_are_bounded_for_mobile_worker(self):
        options = TrackingOptions(max_frames=300, sample_every=3, max_corners=120)
        self.assertEqual(300, options.max_frames)
        self.assertEqual(3, options.sample_every)
        self.assertEqual(120, options.max_corners)
        with self.assertRaisesRegex(ValueError, "TRACKING_MAX_FRAMES_INVALID"):
            TrackingOptions(max_frames=0)
        with self.assertRaisesRegex(ValueError, "TRACKING_SAMPLE_EVERY_INVALID"):
            TrackingOptions(sample_every=31)
        with self.assertRaisesRegex(ValueError, "TRACKING_MAX_CORNERS_INVALID"):
            TrackingOptions(max_corners=5)

    def test_affine_decomposition_returns_translation_rotation_and_scale(self):
        angle = math.radians(30.0)
        scale = 1.25
        matrix = [
            [scale * math.cos(angle), -scale * math.sin(angle), 12.0],
            [scale * math.sin(angle), scale * math.cos(angle), -7.5],
        ]
        result = OpenCvGlobalMotionAnalyzer.decompose_affine(matrix, confidence=0.8)
        self.assertAlmostEqual(12.0, result["dx"], places=6)
        self.assertAlmostEqual(-7.5, result["dy"], places=6)
        self.assertAlmostEqual(30.0, result["rotationDeg"], places=6)
        self.assertAlmostEqual(1.25, result["scale"], places=6)
        self.assertAlmostEqual(0.8, result["confidence"], places=6)

    def test_invalid_affine_or_confidence_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "TRACKING_AFFINE_INVALID"):
            OpenCvGlobalMotionAnalyzer.decompose_affine([[1.0, 0.0]], confidence=1.0)
        with self.assertRaisesRegex(ValueError, "TRACKING_CONFIDENCE_INVALID"):
            OpenCvGlobalMotionAnalyzer.decompose_affine([[1.0, 0.0, 0.0], [0.0, 1.0, 0.0]], confidence=1.1)


if __name__ == "__main__":
    unittest.main()
