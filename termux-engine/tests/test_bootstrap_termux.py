from pathlib import Path
import subprocess
import unittest


class BootstrapTermuxScriptTest(unittest.TestCase):
    def test_bootstrap_enables_external_automation_and_forces_only_safe_engine_restart(self):
        script = Path(__file__).resolve().parents[1] / "bootstrap_termux.sh"
        subprocess.run(["bash", "-n", str(script)], check=True)
        text = script.read_text(encoding="utf-8")
        self.assertIn("--refresh-only --enable-external-automation", text)
        self.assertIn("--once --force-restart", text)
        self.assertIn("--daemon", text)
        self.assertNotIn("pkill", text)
        self.assertNotIn("kill -9", text)
        self.assertNotIn("reset --hard", text)


if __name__ == "__main__":
    unittest.main()
