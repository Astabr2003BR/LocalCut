#!/usr/bin/env python3
from __future__ import annotations

import argparse
from contextlib import contextmanager
import fcntl
import importlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request

REPOSITORY_URL = "https://github.com/Astabr2003BR/LocalCut.git"
REPOSITORY_API = "https://api.github.com/repos/Astabr2003BR/LocalCut"
DEVELOPMENT_BRANCH = "feature/localcut-bridge-v1"
TERMUX_WORKFLOW = "termux-engine-ci.yml"
STATE_DIR = Path.home() / ".localcut-engine"
SOURCE_DIR = STATE_DIR / "source"
START_SCRIPT = STATE_DIR / "start-localcut-engine"
SUPERVISOR_LOCK = STATE_DIR / "supervisor.lock"
AUTOMATION_LOCK = STATE_DIR / "automation.lock"
MAINTENANCE_LOCK = STATE_DIR / "maintenance.lock"
STATUS_FILE = STATE_DIR / "automation-status.json"
LOG_FILE = STATE_DIR / "automation.log"
UPDATE_INTERVAL_SECONDS = 6 * 60 * 60
HEALTH_INTERVAL_SECONDS = 60
REVISION_RE = re.compile(r"^[0-9a-f]{40}$")


class AutomationError(RuntimeError):
    pass


def _run(args: list[str], *, cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        args,
        cwd=str(cwd) if cwd else None,
        check=check,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def _write_status(state: str, *, revision: str | None = None, error: str | None = None) -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    payload = {
        "schemaVersion": 1,
        "state": state,
        "revision": revision,
        "error": error,
        "updatedAtEpochMs": int(time.time() * 1000),
    }
    temp = STATUS_FILE.with_suffix(".tmp")
    temp.write_text(json.dumps(payload, separators=(",", ":")), encoding="utf-8")
    os.chmod(temp, 0o600)
    os.replace(temp, STATUS_FILE)


def _current_revision() -> str | None:
    if not (SOURCE_DIR / ".git").is_dir():
        return None
    result = _run(["git", "rev-parse", "HEAD"], cwd=SOURCE_DIR, check=False)
    return result.stdout.strip() if result.returncode == 0 else None


def _source_is_clean() -> bool:
    result = _run(["git", "status", "--porcelain"], cwd=SOURCE_DIR)
    return not result.stdout.strip()


def _validate_origin() -> None:
    result = _run(["git", "remote", "get-url", "origin"], cwd=SOURCE_DIR)
    if result.stdout.strip().rstrip("/") != REPOSITORY_URL.rstrip("/"):
        raise AutomationError("ENGINE_UPDATE_ORIGIN_MISMATCH")


def _github_json(url: str) -> dict:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "LocalCut-Termux-Automation/1.0",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        if response.status != 200:
            raise AutomationError("ENGINE_UPDATE_VALIDATION_UNAVAILABLE")
        return json.loads(response.read().decode("utf-8"))


def latest_validated_revision() -> str:
    query = urllib.parse.urlencode({
        "branch": DEVELOPMENT_BRANCH,
        "status": "success",
        "per_page": 20,
    })
    payload = _github_json(f"{REPOSITORY_API}/actions/workflows/{TERMUX_WORKFLOW}/runs?{query}")
    runs = payload.get("workflow_runs")
    if not isinstance(runs, list):
        raise AutomationError("ENGINE_UPDATE_VALIDATION_INVALID")
    for run in runs:
        if not isinstance(run, dict):
            continue
        revision = run.get("head_sha")
        if (
            run.get("conclusion") == "success"
            and run.get("head_branch") == DEVELOPMENT_BRANCH
            and isinstance(revision, str)
            and REVISION_RE.fullmatch(revision)
        ):
            return revision
    raise AutomationError("ENGINE_UPDATE_NO_VALIDATED_REVISION")


def _validate_managed_source(source: Path) -> None:
    checks = [
        [sys.executable, "-m", "py_compile", "termux-engine/localcut_automation.py", "termux-engine/localcut_supervisor.py", "termux-engine/localcut_creative_server.py"],
        ["bash", "-n", "termux-engine/setup_termux.sh"],
        ["bash", "-n", "termux-engine/bootstrap_termux.sh"],
        ["bash", "-n", "termux-engine/restart_termux.sh"],
    ]
    for argv in checks:
        result = _run(argv, cwd=source, check=False)
        if result.returncode != 0:
            raise AutomationError("ENGINE_UPDATE_VALIDATION_FAILED")


def _bootstrap_source(revision: str) -> bool:
    if SOURCE_DIR.exists():
        raise AutomationError("ENGINE_SOURCE_INVALID")
    STATE_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    temp = Path(tempfile.mkdtemp(prefix="source-bootstrap-", dir=STATE_DIR))
    try:
        _run(["git", "init", "--quiet"], cwd=temp)
        _run(["git", "remote", "add", "origin", REPOSITORY_URL], cwd=temp)
        _run(["git", "fetch", "--depth", "1", "origin", revision], cwd=temp)
        _run(["git", "checkout", "--detach", "FETCH_HEAD"], cwd=temp)
        _validate_managed_source(temp)
        temp.replace(SOURCE_DIR)
        return True
    finally:
        if temp.exists():
            shutil.rmtree(temp, ignore_errors=True)


def update_source() -> bool:
    target = latest_validated_revision()
    if not (SOURCE_DIR / ".git").is_dir():
        return _bootstrap_source(target)
    _validate_origin()
    if not _source_is_clean():
        raise AutomationError("ENGINE_SOURCE_DIRTY")
    before = _current_revision()
    if target == before:
        return False
    _run(["git", "fetch", "--depth", "64", "origin", target], cwd=SOURCE_DIR)
    ancestry = _run(["git", "merge-base", "--is-ancestor", before or "HEAD", target], cwd=SOURCE_DIR, check=False)
    if ancestry.returncode != 0:
        raise AutomationError("ENGINE_UPDATE_NON_FAST_FORWARD")
    _run(["git", "checkout", "--detach", target], cwd=SOURCE_DIR)
    try:
        _validate_managed_source(SOURCE_DIR)
    except Exception:
        if before and REVISION_RE.fullmatch(before):
            _run(["git", "checkout", "--detach", before], cwd=SOURCE_DIR, check=False)
        raise
    return True


def _module_available(name: str) -> bool:
    try:
        return importlib.util.find_spec(name) is not None
    except (ImportError, AttributeError, ValueError):
        return False


def ensure_toolchain() -> None:
    if shutil.which("pkg") is None:
        missing = [name for name in ("git", "ffmpeg", "ffprobe") if shutil.which(name) is None]
        if missing or not _module_available("cv2"):
            raise AutomationError("ENGINE_TOOLCHAIN_UNAVAILABLE")
        return

    core_missing = [name for name in ("git", "ffmpeg", "ffprobe") if shutil.which(name) is None]
    if core_missing:
        _run(["pkg", "install", "-y", "git", "ffmpeg"])
    if not _module_available("cv2"):
        _run(["pkg", "install", "-y", "x11-repo"])
        importlib.invalidate_caches()
        installed = False
        for package in ("python-opencv-python", "opencv-python"):
            result = _run(["pkg", "install", "-y", package], check=False)
            importlib.invalidate_caches()
            if result.returncode == 0 and _module_available("cv2"):
                installed = True
                break
        if not installed:
            raise AutomationError("ENGINE_OPENCV_UNAVAILABLE")
    if shutil.which("git") is None or shutil.which("ffmpeg") is None or shutil.which("ffprobe") is None:
        raise AutomationError("ENGINE_TOOLCHAIN_UNAVAILABLE")
    if not _module_available("cv2"):
        raise AutomationError("ENGINE_OPENCV_UNAVAILABLE")


def _read_pid() -> int | None:
    try:
        raw = SUPERVISOR_LOCK.read_text(encoding="utf-8").strip()
    except (FileNotFoundError, OSError):
        return None
    if not raw.isdigit():
        return None
    return int(raw)


def _pid_cmdline(pid: int) -> str:
    try:
        return (Path("/proc") / str(pid) / "cmdline").read_bytes().replace(b"\0", b" ").decode("utf-8", "replace")
    except OSError:
        return ""


def engine_running() -> bool:
    pid = _read_pid()
    if pid is None:
        return False
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return "termux-engine/localcut_supervisor.py" in _pid_cmdline(pid)


def stop_engine(timeout_seconds: float = 5.0) -> None:
    pid = _read_pid()
    if pid is None:
        return
    try:
        os.kill(pid, 0)
    except OSError:
        return
    if "termux-engine/localcut_supervisor.py" not in _pid_cmdline(pid):
        raise AutomationError("ENGINE_RESTART_PID_MISMATCH")
    os.kill(pid, signal.SIGTERM)
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            os.kill(pid, 0)
        except OSError:
            return
        time.sleep(0.1)
    raise AutomationError("ENGINE_RESTART_TIMEOUT")


def start_engine() -> None:
    if engine_running():
        return
    if not START_SCRIPT.is_file():
        raise AutomationError("ENGINE_START_SCRIPT_MISSING")
    subprocess.Popen(
        ["bash", str(START_SCRIPT)],
        cwd=str(SOURCE_DIR) if SOURCE_DIR.is_dir() else None,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )


def refresh_launchers() -> None:
    script = SOURCE_DIR / "termux-engine" / "setup_termux.sh"
    if not script.is_file():
        raise AutomationError("ENGINE_SETUP_SCRIPT_MISSING")
    _run(["bash", str(script), "--refresh-only"], cwd=SOURCE_DIR)


@contextmanager
def _exclusive_lock(path: Path):
    STATE_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    with path.open("a+") as handle:
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            yield False
            return
        try:
            yield True
        finally:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def _wait_for_engine(timeout_seconds: float = 5.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if engine_running():
            return
        time.sleep(0.1)
    raise AutomationError("ENGINE_START_TIMEOUT")


def _start_existing_engine_best_effort() -> None:
    if engine_running() or not START_SCRIPT.is_file():
        return
    try:
        start_engine()
        _wait_for_engine()
    except Exception:
        # Maintenance may repair the source/toolchain below. Do not hide the final failure.
        pass


def run_once(*, allow_update: bool = True, repair_toolchain: bool = True, force_restart: bool = False) -> dict:
    with _exclusive_lock(MAINTENANCE_LOCK) as acquired:
        if not acquired:
            return {"changed": False, "revision": _current_revision(), "running": engine_running(), "skipped": True}

        # Recovery is local-first: if a known engine can start, bring it back before any network update.
        _start_existing_engine_best_effort()

        if repair_toolchain:
            ensure_toolchain()
        changed = False
        if allow_update:
            changed = update_source()
            refresh_launchers()
        if (changed or force_restart) and engine_running():
            stop_engine()
        start_engine()
        _wait_for_engine()
        revision = _current_revision()
        _write_status("READY", revision=revision)
        return {"changed": changed, "revision": revision, "running": True, "skipped": False}


def _daemon() -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    with _exclusive_lock(AUTOMATION_LOCK) as acquired:
        if not acquired:
            return
        last_update = 0.0
        while True:
            now = time.monotonic()
            try:
                do_update = now - last_update >= UPDATE_INTERVAL_SECONDS
                result = run_once(allow_update=do_update, repair_toolchain=do_update)
                if do_update:
                    last_update = now
                if result.get("changed"):
                    os.execv(sys.executable, [sys.executable, str(SOURCE_DIR / "termux-engine" / "localcut_automation.py"), "--daemon"])
                _write_status("READY", revision=result.get("revision"))
            except Exception as error:
                _write_status("DEGRADED", revision=_current_revision(), error=str(error))
            time.sleep(HEALTH_INTERVAL_SECONDS)


def main() -> None:
    parser = argparse.ArgumentParser(description="LocalCut Termux automation watchdog")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--once", action="store_true")
    mode.add_argument("--daemon", action="store_true")
    parser.add_argument("--no-update", action="store_true")
    parser.add_argument("--no-repair", action="store_true")
    parser.add_argument("--force-restart", action="store_true")
    args = parser.parse_args()
    try:
        if args.daemon:
            _daemon()
        else:
            run_once(
                allow_update=not args.no_update,
                repair_toolchain=not args.no_repair,
                force_restart=args.force_restart,
            )
    except Exception as error:
        _write_status("FAILED", revision=_current_revision(), error=str(error))
        raise SystemExit(str(error))


if __name__ == "__main__":
    main()
