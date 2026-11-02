from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from localcut_managed_automation import MANAGED_AUTOMATION_CAPABILITY, attach_managed_automation_capability


class ManagedAutomationCapabilityTest(unittest.TestCase):
    def test_capability_is_added_once_without_hiding_existing_capabilities(self):
        class Server:
            def capabilities(self):
                return ["PING", "FFPROBE"]

        server = attach_managed_automation_capability(Server())
        caps = server.capabilities()
        self.assertEqual(["PING", "FFPROBE", MANAGED_AUTOMATION_CAPABILITY], caps)
        self.assertEqual(1, caps.count(MANAGED_AUTOMATION_CAPABILITY))


if __name__ == "__main__":
    unittest.main()
