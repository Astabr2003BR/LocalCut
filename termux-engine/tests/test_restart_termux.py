from pathlib import Path
import subprocess
import unittest


class RestartTermuxScriptTest(unittest.TestCase):
    def test_restart_script_is_valid_bash_and_fails_closed(self):
        script = Path(__file__).resolve().parents[1] / "restart_termux.sh"
        subprocess.run(["bash", "-n", str(script)], check=True)
        text = script.read_text(encoding="utf-8")

        self.assertIn("/proc/$PID/cmdline", text)
        self.assertIn("termux-engine/localcut_supervisor.py", text)
        self.assertIn('kill "$PID"', text)
        self.assertNotIn("pkill", text)
        self.assertNotIn("kill -9", text)
        self.assertNotIn("rm -", text)


if __name__ == "__main__":
    unittest.main()
