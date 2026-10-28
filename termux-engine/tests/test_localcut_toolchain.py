import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import unittest

from localcut_toolchain import TOOL_SPECS, detect_toolchain


class LocalCutToolchainTest(unittest.TestCase):
    def test_detects_only_allowlisted_creative_tools(self):
        found = {
            "ffmpeg": "/usr/bin/ffmpeg",
            "ffprobe": "/usr/bin/ffprobe",
            "blender": "/usr/bin/blender",
        }
        result = detect_toolchain(found.get, module_available=lambda _: False)
        self.assertEqual(["FFMPEG", "FFPROBE", "BLENDER"], result["capabilities"])
        self.assertFalse(next(t for t in result["tools"] if t["capability"] == "KRITA")["available"])

    def test_tool_specs_do_not_expose_shell_or_arbitrary_exec(self):
        commands = {command for spec in TOOL_SPECS for command in spec.commands}
        self.assertNotIn("sh", commands)
        self.assertNotIn("bash", commands)
        self.assertNotIn("python", commands)
        self.assertNotIn("exec", commands)
        self.assertEqual(
            {"FFMPEG", "FFPROBE", "BLENDER", "OPENTOONZ", "KRITA", "GIMP", "GEGL", "OPENCV"},
            {s.capability for s in TOOL_SPECS},
        )

    def test_opentoonz_accepts_common_binary_name_variants(self):
        result = detect_toolchain(
            lambda command: "/opt/opentoonz" if command == "OpenToonz" else None,
            module_available=lambda _: False,
        )
        tool = next(t for t in result["tools"] if t["capability"] == "OPENTOONZ")
        self.assertTrue(tool["available"])
        self.assertEqual("/opt/opentoonz", tool["executable"])

    def test_gimp_accepts_versioned_binary_and_gegl(self):
        found = {
            "gimp-3.0": "/usr/bin/gimp-3.0",
            "gegl": "/usr/bin/gegl",
        }
        result = detect_toolchain(found.get, module_available=lambda _: False)
        self.assertIn("GIMP", result["capabilities"])
        self.assertIn("GEGL", result["capabilities"])
        gimp = next(t for t in result["tools"] if t["capability"] == "GIMP")
        self.assertEqual("/usr/bin/gimp-3.0", gimp["executable"])

    def test_opencv_is_detected_as_python_module_not_shell_command(self):
        result = detect_toolchain(
            which=lambda _: None,
            module_available=lambda name: name == "cv2",
        )
        opencv = next(t for t in result["tools"] if t["capability"] == "OPENCV")
        self.assertTrue(opencv["available"])
        self.assertIsNone(opencv["executable"])
        self.assertEqual("cv2", opencv["pythonModule"])
        self.assertIn("OPENCV", result["capabilities"])

    def test_missing_opencv_module_stays_unavailable(self):
        result = detect_toolchain(which=lambda _: None, module_available=lambda _: False)
        opencv = next(t for t in result["tools"] if t["capability"] == "OPENCV")
        self.assertFalse(opencv["available"])
        self.assertIsNone(opencv["pythonModule"])


if __name__ == "__main__":
    unittest.main()
