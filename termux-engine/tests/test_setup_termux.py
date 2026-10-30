from pathlib import Path
import subprocess
import unittest


class SetupTermuxScriptTest(unittest.TestCase):
    def test_setup_installs_automatic_recovery_hooks_without_destructive_commands(self):
        script = Path(__file__).resolve().parents[1] / "setup_termux.sh"
        subprocess.run(["bash", "-n", str(script)], check=True)
        text = script.read_text(encoding="utf-8")
        self.assertIn("--enable-external-automation", text)
        self.assertIn("allow-external-apps = true", text)
        self.assertIn("auto-recover", text)
        self.assertIn("--daemon", text)
        self.assertIn("--refresh-only", text)
        self.assertNotIn("pkill", text)
        self.assertNotIn("kill -9", text)
        self.assertNotIn("reset --hard", text)


if __name__ == "__main__":
    unittest.main()
