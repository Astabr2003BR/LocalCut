import json
import socket
import threading
import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from localcut_engine import create_server, generate_token


class LocalCutEngineTest(unittest.TestCase):
    def setUp(self):
        self.token = generate_token()
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
        probe.close()
        self.server = create_server("127.0.0.1", port, self.token)
        self.port = port
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def exchange(self, payload):
        raw = (json.dumps(payload, separators=(",", ":")) + "\n").encode()
        with socket.create_connection(("127.0.0.1", self.port), timeout=2) as sock:
            sock.sendall(raw)
            file = sock.makefile("rb")
            return json.loads(file.readline().decode())

    def request(self, operation, **overrides):
        payload = {
            "protocolVersion": 1,
            "projectId": "localcut",
            "requestId": "request-12345678",
            "token": self.token,
            "operation": operation,
        }
        payload.update(overrides)
        return self.exchange(payload)

    def test_authenticated_read_only_contract(self):
        ping = self.request("PING")
        self.assertTrue(ping["ok"])
        self.assertEqual("READY", ping["status"])
        status = self.request("GET_STATUS")
        self.assertEqual("localcut", status["projectId"])
        self.assertEqual("READY", status["status"])
        caps = self.request("GET_CAPABILITIES")
        self.assertEqual(["PING", "GET_STATUS", "GET_CAPABILITIES"], caps["capabilities"])

    def test_auth_protocol_and_project_fail_closed(self):
        self.assertEqual("AUTH_INVALID", self.request("PING", token="x" * 43)["code"])
        self.assertEqual("PROTOCOL_MISMATCH", self.request("PING", protocolVersion=2)["code"])
        self.assertEqual("PROJECT_MISMATCH", self.request("PING", projectId="other")["code"])

    def test_arbitrary_operation_is_not_exposed(self):
        result = self.request("EXEC", command="rm -rf /")
        self.assertFalse(result["ok"])
        self.assertEqual("OPERATION_NOT_ALLOWED", result["code"])

    def test_malformed_and_oversized_requests_are_rejected(self):
        with socket.create_connection(("127.0.0.1", self.port), timeout=2) as sock:
            sock.sendall(b"not-json\n")
            response = json.loads(sock.makefile("rb").readline().decode())
        self.assertEqual("REQUEST_INVALID", response["code"])
        with socket.create_connection(("127.0.0.1", self.port), timeout=2) as sock:
            sock.sendall((b"x" * 5000) + b"\n")
            response = json.loads(sock.makefile("rb").readline().decode())
        self.assertEqual("REQUEST_INVALID", response["code"])

    def test_non_loopback_and_weak_token_are_rejected_before_bind(self):
        with self.assertRaisesRegex(ValueError, "ENGINE_BIND_LOOPBACK_ONLY"):
            create_server("0.0.0.0", 43127, self.token)
        with self.assertRaisesRegex(ValueError, "ENGINE_TOKEN_INVALID"):
            create_server("127.0.0.1", 43127, "short")


if __name__ == "__main__":
    unittest.main()
