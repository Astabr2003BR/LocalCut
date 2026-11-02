from pathlib import Path
import unittest


class PortableSetupScriptTest(unittest.TestCase):
    def test_setup_supports_termux_and_ubuntu_userland(self):
        script = (Path(__file__).resolve().parents[1] / "setup_termux.sh").read_text(encoding="utf-8")
        self.assertIn("command -v python3", script)
        self.assertIn("apt update && apt install -y python3", script)
        self.assertIn("Ubuntu/UserLAnd/Linux", script)
        self.assertIn("TERMUX_MODE", script)
        self.assertIn("$HOME/.termux/boot", script)
        self.assertNotIn("#!/data/data/com.termux/files/usr/bin/bash\nset -euo pipefail", script)

    def test_linux_launcher_uses_detected_python_without_pkg_dependency(self):
        script = (Path(__file__).resolve().parents[1] / "setup_termux.sh").read_text(encoding="utf-8")
        self.assertIn("PYTHON_BIN", script)
        self.assertIn("start-localcut-engine", script)
        self.assertIn("localcut_supervisor.py --pair", script)
        self.assertNotIn("command -v python >/dev/null 2>&1 || {", script)


if __name__ == "__main__":
    unittest.main()
