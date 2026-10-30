from pathlib import Path
import tempfile
import unittest
from unittest import mock
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import localcut_automation as automation


class LocalCutAutomationTest(unittest.TestCase):
    def test_origin_mismatch_fails_closed(self):
        with mock.patch.object(automation, "_run") as run:
            run.return_value = mock.Mock(stdout="https://evil.example/repo.git\n", returncode=0)
            with self.assertRaisesRegex(automation.AutomationError, "ENGINE_UPDATE_ORIGIN_MISMATCH"):
                automation._validate_origin()

    def test_validated_revision_comes_only_from_successful_termux_ci_on_target_branch(self):
        good = "a" * 40
        with mock.patch.object(automation, "_github_json", return_value={"workflow_runs": [
            {"conclusion": "failure", "head_branch": automation.DEVELOPMENT_BRANCH, "head_sha": "b" * 40},
            {"conclusion": "success", "head_branch": "other", "head_sha": "c" * 40},
            {"conclusion": "success", "head_branch": automation.DEVELOPMENT_BRANCH, "head_sha": good},
        ]}):
            self.assertEqual(good, automation.latest_validated_revision())

    def test_engine_running_requires_supervisor_cmdline(self):
        with mock.patch.object(automation, "_read_pid", return_value=123), \
             mock.patch.object(automation.os, "kill"), \
             mock.patch.object(automation, "_pid_cmdline", return_value="python unrelated.py"):
            self.assertFalse(automation.engine_running())

        with mock.patch.object(automation, "_read_pid", return_value=123), \
             mock.patch.object(automation.os, "kill"), \
             mock.patch.object(automation, "_pid_cmdline", return_value="python termux-engine/localcut_supervisor.py"):
            self.assertTrue(automation.engine_running())

    def test_stop_engine_rejects_stale_or_unrelated_pid(self):
        with mock.patch.object(automation, "_read_pid", return_value=456), \
             mock.patch.object(automation.os, "kill"), \
             mock.patch.object(automation, "_pid_cmdline", return_value="python other.py"):
            with self.assertRaisesRegex(automation.AutomationError, "ENGINE_RESTART_PID_MISMATCH"):
                automation.stop_engine()

    def test_run_once_restarts_only_when_validated_source_changed(self):
        calls = []
        with mock.patch.object(automation, "_exclusive_lock") as lock_ctx, \
             mock.patch.object(automation, "_start_existing_engine_best_effort"), \
             mock.patch.object(automation, "ensure_toolchain", side_effect=lambda: calls.append("toolchain")), \
             mock.patch.object(automation, "update_source", return_value=True), \
             mock.patch.object(automation, "refresh_launchers", side_effect=lambda: calls.append("refresh")), \
             mock.patch.object(automation, "engine_running", side_effect=[True, True]), \
             mock.patch.object(automation, "stop_engine", side_effect=lambda: calls.append("stop")), \
             mock.patch.object(automation, "start_engine", side_effect=lambda: calls.append("start")), \
             mock.patch.object(automation, "_current_revision", return_value="abc"), \
             mock.patch.object(automation, "_wait_for_engine"), \
             mock.patch.object(automation, "_write_status"):
            lock_ctx.return_value.__enter__.return_value = True
            lock_ctx.return_value.__exit__.return_value = False
            result = automation.run_once()
        self.assertEqual(["toolchain", "refresh", "stop", "start"], calls)
        self.assertTrue(result["changed"])

    def test_force_restart_restarts_even_when_source_is_current(self):
        calls = []
        with mock.patch.object(automation, "_exclusive_lock") as lock_ctx, \
             mock.patch.object(automation, "_start_existing_engine_best_effort"), \
             mock.patch.object(automation, "ensure_toolchain"), \
             mock.patch.object(automation, "update_source", return_value=False), \
             mock.patch.object(automation, "refresh_launchers"), \
             mock.patch.object(automation, "engine_running", side_effect=[True, True]), \
             mock.patch.object(automation, "stop_engine", side_effect=lambda: calls.append("stop")), \
             mock.patch.object(automation, "start_engine", side_effect=lambda: calls.append("start")), \
             mock.patch.object(automation, "_current_revision", return_value="abc"), \
             mock.patch.object(automation, "_wait_for_engine"), \
             mock.patch.object(automation, "_write_status"):
            lock_ctx.return_value.__enter__.return_value = True
            lock_ctx.return_value.__exit__.return_value = False
            result = automation.run_once(force_restart=True)
        self.assertEqual(["stop", "start"], calls)
        self.assertFalse(result["changed"])

    def test_update_source_refuses_dirty_managed_tree(self):
        with tempfile.TemporaryDirectory() as td:
            source = Path(td) / "source"
            (source / ".git").mkdir(parents=True)
            with mock.patch.object(automation, "SOURCE_DIR", source), \
                 mock.patch.object(automation, "latest_validated_revision", return_value="a" * 40), \
                 mock.patch.object(automation, "_validate_origin"), \
                 mock.patch.object(automation, "_source_is_clean", return_value=False):
                with self.assertRaisesRegex(automation.AutomationError, "ENGINE_SOURCE_DIRTY"):
                    automation.update_source()

    def test_local_engine_is_started_before_network_maintenance(self):
        order = []
        with mock.patch.object(automation, "_exclusive_lock") as lock_ctx, \
             mock.patch.object(automation, "_start_existing_engine_best_effort", side_effect=lambda: order.append("local")), \
             mock.patch.object(automation, "ensure_toolchain", side_effect=lambda: order.append("toolchain")), \
             mock.patch.object(automation, "update_source", side_effect=automation.AutomationError("network")):
            lock_ctx.return_value.__enter__.return_value = True
            lock_ctx.return_value.__exit__.return_value = False
            with self.assertRaisesRegex(automation.AutomationError, "network"):
                automation.run_once()
        self.assertEqual(["local", "toolchain"], order)


if __name__ == "__main__":
    unittest.main()
