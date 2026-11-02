from pathlib import Path
import tempfile
import unittest
from unittest import mock
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import localcut_private_proxy as proxy


class PrivateProxyTest(unittest.TestCase):
    def test_policy_rejects_public_and_accepts_rfc1918(self):
        self.assertTrue(proxy._is_rfc1918("10.0.0.2"))
        self.assertTrue(proxy._is_rfc1918("172.16.1.2"))
        self.assertTrue(proxy._is_rfc1918("192.168.1.2"))
        self.assertFalse(proxy._is_rfc1918("8.8.8.8"))
        self.assertFalse(proxy._is_rfc1918("127.0.0.1"))

    def test_rejects_public_bind(self):
        with self.assertRaisesRegex(ValueError, "PROXY_BIND_PRIVATE_IPV4_REQUIRED"):
            proxy.create_proxy_servers("8.8.8.8", 43227, 43228)

    def test_discovery_prefers_cached_bindable_private_ip(self):
        with tempfile.TemporaryDirectory() as td:
            state = Path(td) / "private-ipv4"
            state.write_text("192.168.1.103\n")
            with mock.patch.object(proxy, "_can_bind", side_effect=lambda host: host == "192.168.1.103"), \
                 mock.patch.object(proxy, "_route_candidates", return_value=[]), \
                 mock.patch.object(proxy, "_hostname_candidates", return_value=[]), \
                 mock.patch.object(proxy, "_command_candidates", return_value=[]), \
                 mock.patch.object(proxy, "_proc_candidates", return_value=[]):
                self.assertEqual(proxy.discover_private_ipv4(state), "192.168.1.103")

    def test_stale_cached_ip_falls_back_to_other_sources(self):
        with tempfile.TemporaryDirectory() as td:
            state = Path(td) / "private-ipv4"
            state.write_text("192.168.50.99\n")
            with mock.patch.object(proxy, "_can_bind", side_effect=lambda host: host == "10.0.0.7"), \
                 mock.patch.object(proxy, "_route_candidates", return_value=["10.0.0.7"]), \
                 mock.patch.object(proxy, "_hostname_candidates", return_value=[]), \
                 mock.patch.object(proxy, "_command_candidates", return_value=[]), \
                 mock.patch.object(proxy, "_proc_candidates", return_value=[]):
                self.assertEqual(proxy.discover_private_ipv4(state), "10.0.0.7")

    def test_persist_private_ip_is_private_mode_and_roundtrips(self):
        with tempfile.TemporaryDirectory() as td:
            state = Path(td) / "private-ipv4"
            proxy._persist_private_ipv4(state, "192.168.1.103")
            self.assertEqual(state.read_text().strip(), "192.168.1.103")
            self.assertEqual(state.stat().st_mode & 0o777, 0o600)


if __name__ == "__main__":
    unittest.main()
