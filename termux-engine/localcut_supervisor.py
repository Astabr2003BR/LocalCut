#!/usr/bin/env python3
from __future__ import annotations

import argparse
import fcntl
import os
from pathlib import Path
import threading

from localcut_agent import ApprovalStore, DeveloperAgentPolicy, SafeAgentExecutor, TermuxNotificationSink
from localcut_agent_rpc import DEFAULT_AGENT_PORT, create_agent_server
from localcut_engine import (
    DEFAULT_CREDENTIALS_FILE,
    DEFAULT_ENGINE_DIR,
    DEFAULT_HOST,
    DEFAULT_JOBS_FILE,
    DEFAULT_PORT,
    DEFAULT_SPOOL_DIR,
    EngineCredentialStore,
    PairingWindow,
    create_server,
    format_pairing_code,
    generate_pairing_code,
    _validate_token,
)
from localcut_jobs import FfprobeMediaAnalyzer, JobStore, MediaSpool

DEFAULT_APPROVALS_FILE = DEFAULT_ENGINE_DIR / "approvals.sqlite3"
DEFAULT_LOCK_FILE = DEFAULT_ENGINE_DIR / "supervisor.lock"
DEFAULT_WORKSPACE = Path(__file__).resolve().parents[1]


class InstanceLock:
    def __init__(self, path: str | Path = DEFAULT_LOCK_FILE):
        self.path = Path(path).expanduser()
        self._fd: int | None = None

    def acquire(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.path.parent, 0o700)
        fd = os.open(self.path, os.O_RDWR | os.O_CREAT, 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            os.close(fd)
            raise RuntimeError("ENGINE_ALREADY_RUNNING") from exc
        os.ftruncate(fd, 0)
        os.write(fd, str(os.getpid()).encode("ascii"))
        os.fsync(fd)
        os.chmod(self.path, 0o600)
        self._fd = fd

    def release(self) -> None:
        if self._fd is None:
            return
        try:
            fcntl.flock(self._fd, fcntl.LOCK_UN)
        finally:
            os.close(self._fd)
            self._fd = None

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, exc_type, exc, tb):
        self.release()


def _load_token(args) -> tuple[str, PairingWindow | None]:
    env_token = os.environ.get("LOCALCUT_ENGINE_TOKEN", "").strip()
    credential_store = EngineCredentialStore(args.credentials_file)
    if env_token:
        token = _validate_token(env_token)
    elif args.pair:
        token = credential_store.load_or_create()
    else:
        token = credential_store.load()
        if token is None:
            raise SystemExit("ENGINE_NOT_INITIALIZED: start once with --pair")

    pairing_window = None
    if args.pair:
        pairing_code = generate_pairing_code()
        pairing_window = PairingWindow(pairing_code)
        print(f"LOCALCUT_PAIRING_CODE={format_pairing_code(pairing_code)}", flush=True)
    return token, pairing_window


def main() -> None:
    parser = argparse.ArgumentParser(description="LocalCut Termux Agent Engine supervisor")
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--agent-port", type=int, default=DEFAULT_AGENT_PORT)
    parser.add_argument("--pair", action="store_true")
    parser.add_argument("--credentials-file", default=str(DEFAULT_CREDENTIALS_FILE))
    parser.add_argument("--jobs-file", default=str(DEFAULT_JOBS_FILE))
    parser.add_argument("--spool-dir", default=str(DEFAULT_SPOOL_DIR))
    parser.add_argument("--approvals-file", default=str(DEFAULT_APPROVALS_FILE))
    parser.add_argument("--workspace", default=str(DEFAULT_WORKSPACE))
    parser.add_argument("--lock-file", default=str(DEFAULT_LOCK_FILE))
    args = parser.parse_args()

    if args.host != DEFAULT_HOST:
        raise SystemExit("ENGINE_BIND_LOOPBACK_ONLY")
    if args.agent_port == args.port:
        raise SystemExit("ENGINE_PORT_CONFLICT")

    with InstanceLock(args.lock_file):
        token, pairing_window = _load_token(args)
        job_store = JobStore(args.jobs_file)
        recovered = job_store.recover_interrupted_jobs()
        if recovered:
            print(f"LOCALCUT_RECOVERED_INTERRUPTED_JOBS={recovered}", flush=True)
        media_spool = MediaSpool(args.spool_dir)
        analyzer = FfprobeMediaAnalyzer()

        policy = DeveloperAgentPolicy(args.workspace)
        executor = SafeAgentExecutor(policy)
        approvals = ApprovalStore(args.approvals_file)
        notifications = TermuxNotificationSink()

        media_server = create_server(
            args.host,
            args.port,
            token,
            pairing_window=pairing_window,
            job_store=job_store,
            media_spool=media_spool,
            analyzer=analyzer,
        )
        agent_server = create_agent_server(
            args.host,
            args.agent_port,
            token,
            policy,
            executor,
            approvals,
            notifications,
        )
        agent_thread = threading.Thread(target=agent_server.serve_forever, daemon=True, name="localcut-agent-rpc")
        agent_thread.start()
        print(
            f"LOCALCUT_ENGINE_READY=media:{args.port},agent:{args.agent_port},ffprobe:{int(analyzer.available)}",
            flush=True,
        )
        try:
            media_server.serve_forever(poll_interval=0.5)
        except KeyboardInterrupt:
            pass
        finally:
            media_server.shutdown()
            agent_server.shutdown()
            media_server.server_close()
            agent_server.server_close()
            agent_thread.join(timeout=2)


if __name__ == "__main__":
    main()
