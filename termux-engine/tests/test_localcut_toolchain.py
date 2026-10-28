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
        result = detect_toolchain(found.get)
        self.assertEqual(["FFMPEG", "FFPROBE", "BLENDER"], result["capabilities"])
        self.assertFalse(next(t for t in result["tools"] if t["capability"] == "KRITA")["available"])

    def test_tool_specs_do_not_expose_shell_or_arbitrary_exec(self):
        commands = {command for spec in TOOL_SPECS for command in spec.commands}
        self.assertNotIn("sh", commands)
        self.assertNotIn("bash", commands)
        self.assertNotIn("python", commands)
        self.assertNotIn("exec", commands)
        self.assertEqual({"FFMPEG", "FFPROBE", "BLENDER", "OPENTOONZ", "KRITA"}, {s.capability for s in TOOL_SPECS})

    def test_opentoonz_accepts_common_binary_name_variants(self):
        result = detect_toolchain(lambda command: "/opt/opentoonz" if command == "OpenToonz" else None)
        tool = next(t for t in result["tools"] if t["capability"] == "OPENTOONZ")
        self.assertTrue(tool["available"])
        self.assertEqual("/opt/opentoonz", tool["executable"])


if __name__ == "__main__":
    unittest.main()
