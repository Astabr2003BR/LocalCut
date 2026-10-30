import json
import socket
import stat
import tempfile
import threading
import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from localcut_engine import (
    EngineCredentialStore,
    PairingWindow,
    create_server,
    generate_pairing_code,
    generate_token,
)


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


class LocalCutEnginePairingTest(unittest.TestCase):
    def free_port(self):
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
        probe.close()
        return port

    def exchange(self, port, payload):
        raw = (json.dumps(payload, separators=(",", ":")) + "\n").encode()
        with socket.create_connection(("127.0.0.1", port), timeout=2) as sock:
            sock.sendall(raw)
            return json.loads(sock.makefile("rb").readline().decode())

    def test_pairing_code_is_high_entropy_and_human_safe(self):
        code = generate_pairing_code()
        self.assertEqual(12, len(code))
        self.assertTrue(all(ch in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" for ch in code))

    def test_pairing_is_one_time_then_token_authenticates_ping(self):
        token = "t" * 43
        code = "23456789ABCD"
        window = PairingWindow(code=code, ttl_seconds=300, max_attempts=5)
        port = self.free_port()
        server = create_server("127.0.0.1", port, token, pairing_window=window)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            pair = self.exchange(port, {
                "protocolVersion": 1,
                "projectId": "localcut",
                "requestId": "pair-12345678",
                "operation": "PAIR",
                "pairingCode": "2345-6789-ABCD",
            })
            self.assertTrue(pair["ok"])
            self.assertEqual(token, pair["token"])
            second = self.exchange(port, {
                "protocolVersion": 1,
                "projectId": "localcut",
                "requestId": "pair-87654321",
                "operation": "PAIR",
                "pairingCode": code,
            })
            self.assertFalse(second["ok"])
            self.assertEqual("PAIRING_NOT_AVAILABLE", second["code"])
            ping = self.exchange(port, {
                "protocolVersion": 1,
                "projectId": "localcut",
                "requestId": "ping-12345678",
                "token": token,
                "operation": "PING",
            })
            self.assertTrue(ping["ok"])
            self.assertEqual("READY", ping["status"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_pairing_wrong_code_locks_after_bounded_attempts(self):
        token = "t" * 43
        window = PairingWindow(code="23456789ABCD", ttl_seconds=300, max_attempts=2)
        port = self.free_port()
        server = create_server("127.0.0.1", port, token, pairing_window=window)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            for request_id, expected in [
                ("pair-wrong-01", "PAIRING_INVALID"),
                ("pair-wrong-02", "PAIRING_LOCKED"),
            ]:
                result = self.exchange(port, {
                    "protocolVersion": 1,
                    "projectId": "localcut",
                    "requestId": request_id,
                    "operation": "PAIR",
                    "pairingCode": "ZZZZZZZZZZZZ",
                })
                self.assertEqual(expected, result["code"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_pairing_expires_fail_closed(self):
        now = [100.0]
        window = PairingWindow(
            code="23456789ABCD",
            ttl_seconds=30,
            max_attempts=5,
            clock=lambda: now[0],
        )
        now[0] = 131.0
        result = window.claim("23456789ABCD")
        self.assertEqual("PAIRING_EXPIRED", result)

    def test_credentials_are_private_and_round_trip(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "engine" / "credentials.json"
            store = EngineCredentialStore(path)
            token = store.load_or_create()
            self.assertEqual(token, store.load())
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            self.assertEqual(0o700, stat.S_IMODE(path.parent.stat().st_mode))


if __name__ == "__main__":
    unittest.main()
