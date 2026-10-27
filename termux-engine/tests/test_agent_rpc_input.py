import io
from pathlib import Path
import sys
import types
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
stub = types.ModuleType("localcut_workspace")
stub.WorkspaceAccess = object
sys.modules.setdefault("localcut_workspace", stub)
from localcut_agent_rpc import LocalCutAgentRpcHandler


class AgentRpcInputTest(unittest.TestCase):
    def test_safe_operation_receives_structured_input(self):
        calls = []

        class Policy:
            repository = "Astabr2003BR/LocalCut"
            def classify(self, operation):
                return "SAFE"

        class Executor:
            def execute(self, operation, input):
                calls.append((operation, input))
                return {"exitCode": 0, "operation": operation}

        fake = types.SimpleNamespace(
            server=types.SimpleNamespace(policy=Policy(), executor=Executor()),
            wfile=io.BytesIO(),
        )
        payload = {
            "expectedBranch": "feature/x",
            "message": "feat: automatic patch",
            "paths": ["README.md"],
        }

        LocalCutAgentRpcHandler._run_agent_operation(
            fake,
            "request-12345678",
            {"agentOperation": "GIT_COMMIT", "input": payload},
        )

        self.assertEqual([("GIT_COMMIT", payload)], calls)
        self.assertIn(b'"status":"SUCCEEDED"', fake.wfile.getvalue())


if __name__ == "__main__":
    unittest.main()
