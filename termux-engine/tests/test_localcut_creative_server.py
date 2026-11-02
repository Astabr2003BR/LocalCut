import socket
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localcut_creative_server import create_toolchain_server, validate_toolchain_capabilities
from localcut_engine import generate_token


class CreativeToolchainCapabilitiesTest(unittest.TestCase):
    def free_port(self):
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
        probe.close()
        return port

    def test_server_advertises_only_explicit_allowlisted_creative_tools(self):
        server = create_toolchain_server(
            "127.0.0.1",
            self.free_port(),
            generate_token(),
            toolchain_capabilities=("BLENDER", "GIMP", "GEGL", "GIMP"),
        )
        try:
            capabilities = server.capabilities()
            self.assertIn("PING", capabilities)
            self.assertIn("GET_CAPABILITIES", capabilities)
            self.assertEqual(1, capabilities.count("GIMP"))
            self.assertTrue({"BLENDER", "GIMP", "GEGL"}.issubset(capabilities))
            self.assertTrue(all("/" not in capability for capability in capabilities))
        finally:
            server.server_close()

    def test_unknown_or_shell_capability_fails_before_bind(self):
        with self.assertRaisesRegex(ValueError, "TOOLCHAIN_CAPABILITY_INVALID"):
            validate_toolchain_capabilities(("BASH",))
        with self.assertRaisesRegex(ValueError, "TOOLCHAIN_CAPABILITY_INVALID"):
            create_toolchain_server(
                "127.0.0.1",
                self.free_port(),
                generate_token(),
                toolchain_capabilities=("EXEC",),
            )


if __name__ == "__main__":
    unittest.main()
