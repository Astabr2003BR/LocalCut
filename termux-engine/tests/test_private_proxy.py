from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from localcut_private_proxy import _is_rfc1918, create_proxy_servers


class PrivateProxyTest(unittest.TestCase):
    def test_policy_rejects_public_and_accepts_rfc1918(self):
        self.assertTrue(_is_rfc1918("10.0.0.2"))
        self.assertTrue(_is_rfc1918("172.16.1.2"))
        self.assertTrue(_is_rfc1918("192.168.1.2"))
        self.assertFalse(_is_rfc1918("8.8.8.8"))
        self.assertFalse(_is_rfc1918("127.0.0.1"))

    def test_rejects_public_bind(self):
        with self.assertRaisesRegex(ValueError, "PROXY_BIND_PRIVATE_IPV4_REQUIRED"):
            create_proxy_servers("8.8.8.8", 43227, 43228)


if __name__ == "__main__":
    unittest.main()
