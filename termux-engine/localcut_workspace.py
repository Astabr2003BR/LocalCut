from __future__ import annotations

import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import tempfile

MAX_READ_BYTES = 262_144
MAX_PATCH_TEXT_CHARS = 12_000
MAX_LIST_FILES = 500
ALLOWED_REPOSITORY = "Astabr2003BR/LocalCut"
_ALLOWED_BRANCH_PREFIXES = ("feature/", "fix/", "chore/", "test/")
_DENIED_DIR_NAMES = {".git", ".gradle", ".idea", "node_modules", "build", "dist", ".localcut-engine"}
_DENIED_FILE_NAMES = {"local.properties", "gradle.properties", ".npmrc", ".pypirc"}
_DENIED_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".der"}
_SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class WorkspaceAccess:
    def __init__(self, workspace: str | Path):
        self.workspace = Path(workspace).expanduser().resolve()
        if not self.workspace.is_dir():
            raise ValueError("WORKSPACE_INVALID")

    def status(self) -> dict:
        root = self._git("rev-parse", "--show-toplevel").strip()
        if Path(root).resolve() != self.workspace:
            raise ValueError("WORKSPACE_GIT_ROOT_MISMATCH")
        branch = self._git("branch", "--show-current").strip()
        head = self._git("rev-parse", "HEAD").strip()
        dirty = bool(self._git("status", "--porcelain").strip())
        return {"branch": branch, "head": head, "dirty": dirty}

    def list_files(self, prefix: str = "", limit: int = 200) -> dict:
        if isinstance(limit, bool) or not isinstance(limit, int) or not (1 <= limit <= MAX_LIST_FILES):
            raise ValueError("WORKSPACE_LIMIT_INVALID")
        base = self._resolve(prefix, allow_empty=True, require_exists=True)
        if not base.is_dir():
            raise ValueError("WORKSPACE_PATH_NOT_DIRECTORY")
        files: list[dict] = []
        for root, dirs, names in os.walk(base, followlinks=False):
            root_path = Path(root)
            dirs[:] = [
                name for name in sorted(dirs)
                if name not in _DENIED_DIR_NAMES and not (root_path / name).is_symlink()
            ]
            for name in sorted(names):
                path = root_path / name
                if path.is_symlink() or not path.is_file():
                    continue
                rel = path.relative_to(self.workspace).as_posix()
                if self._is_sensitive(PurePosixPath(rel)):
                    continue
                files.append({"path": rel, "sizeBytes": path.stat().st_size})
                if len(files) >= limit:
                    return {"files": files, "truncated": True}
        return {"files": files, "truncated": False}

    def read_file(self, raw_path: str) -> dict:
        path = self._resolve(raw_path, require_exists=True)
        if not path.is_file():
            raise ValueError("WORKSPACE_PATH_NOT_FILE")
        size = path.stat().st_size
        if size > MAX_READ_BYTES:
            raise ValueError("WORKSPACE_FILE_TOO_LARGE")
        data = path.read_bytes()
        if b"\x00" in data:
            raise ValueError("WORKSPACE_FILE_BINARY")
        try:
            content = data.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise ValueError("WORKSPACE_FILE_NOT_UTF8") from exc
        return {
            "path": path.relative_to(self.workspace).as_posix(),
            "sha256": _sha256(data),
            "sizeBytes": len(data),
            "content": content,
        }

    def apply_text_patch(
        self,
        raw_path: str,
        expected_sha256: str,
        old_text: str,
        new_text: str,
    ) -> dict:
        self._assert_safe_write_branch()
        if not isinstance(expected_sha256, str) or not _SHA256_RE.fullmatch(expected_sha256):
            raise ValueError("WORKSPACE_SHA256_INVALID")
        if not isinstance(old_text, str) or not old_text:
            raise ValueError("WORKSPACE_OLD_TEXT_INVALID")
        if not isinstance(new_text, str):
            raise ValueError("WORKSPACE_NEW_TEXT_INVALID")
        if len(old_text) > MAX_PATCH_TEXT_CHARS or len(new_text) > MAX_PATCH_TEXT_CHARS:
            raise ValueError("WORKSPACE_PATCH_TOO_LARGE")

        current = self.read_file(raw_path)
        if current["sha256"] != expected_sha256:
            raise ValueError("WORKSPACE_REVISION_CONFLICT")
        content = current["content"]
        occurrences = content.count(old_text)
        if occurrences == 0:
            raise ValueError("WORKSPACE_PATCH_TARGET_NOT_FOUND")
        if occurrences != 1:
            raise ValueError("WORKSPACE_PATCH_TARGET_AMBIGUOUS")
        updated = content.replace(old_text, new_text, 1)
        data = updated.encode("utf-8")
        if len(data) > MAX_READ_BYTES:
            raise ValueError("WORKSPACE_FILE_TOO_LARGE")

        path = self._resolve(raw_path, require_exists=True)
        mode = path.stat().st_mode & 0o777
        fd, temp_name = tempfile.mkstemp(prefix=f".{path.name}.localcut-", dir=str(path.parent))
        try:
            with os.fdopen(fd, "wb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.chmod(temp_name, mode)
            os.replace(temp_name, path)
        finally:
            if os.path.exists(temp_name):
                os.unlink(temp_name)
        return {
            "path": path.relative_to(self.workspace).as_posix(),
            "sha256": _sha256(data),
            "sizeBytes": len(data),
            "changed": True,
        }

    def _assert_safe_write_branch(self) -> None:
        status = self.status()
        remote = self._git("remote", "get-url", "origin").strip()
        if self._normalize_origin(remote) != ALLOWED_REPOSITORY.lower():
            raise ValueError("WORKSPACE_REPOSITORY_MISMATCH")
        branch = status["branch"]
        if not branch or branch in {"main", "master"}:
            raise ValueError("WORKSPACE_PROTECTED_BRANCH")
        if not branch.startswith(_ALLOWED_BRANCH_PREFIXES):
            raise ValueError("WORKSPACE_BRANCH_NOT_ALLOWED")

    @staticmethod
    def _normalize_origin(remote: str) -> str:
        value = remote.strip().removesuffix(".git")
        for prefix in ("https://github.com/", "http://github.com/", "ssh://git@github.com/"):
            if value.lower().startswith(prefix):
                return value[len(prefix):].strip("/").lower()
        if value.lower().startswith("git@github.com:"):
            return value[len("git@github.com:"):].strip("/").lower()
        return ""

    def _resolve(self, raw_path: str, *, allow_empty: bool = False, require_exists: bool) -> Path:
        if not isinstance(raw_path, str):
            raise ValueError("WORKSPACE_PATH_INVALID")
        raw_path = raw_path.strip().replace("\\", "/")
        if allow_empty and raw_path in {"", "."}:
            return self.workspace
        if not raw_path or len(raw_path) > 512:
            raise ValueError("WORKSPACE_PATH_INVALID")
        pure = PurePosixPath(raw_path)
        if pure.is_absolute() or any(part in {"", ".", ".."} for part in pure.parts):
            raise ValueError("WORKSPACE_PATH_INVALID")
        if self._is_sensitive(pure):
            raise ValueError("WORKSPACE_PATH_DENIED")
        candidate = self.workspace.joinpath(*pure.parts)
        resolved = candidate.resolve(strict=require_exists)
        try:
            resolved.relative_to(self.workspace)
        except ValueError as exc:
            raise ValueError("WORKSPACE_PATH_OUTSIDE") from exc
        if candidate.is_symlink():
            raise ValueError("WORKSPACE_SYMLINK_DENIED")
        return resolved

    @staticmethod
    def _is_sensitive(path: PurePosixPath) -> bool:
        parts = [part.lower() for part in path.parts]
        if any(part in _DENIED_DIR_NAMES or part in {"secrets", "credentials"} for part in parts[:-1]):
            return True
        name = parts[-1] if parts else ""
        if name in _DENIED_FILE_NAMES or name.startswith(".env"):
            return True
        if PurePosixPath(name).suffix.lower() in _DENIED_SUFFIXES:
            return True
        return False

    def _git(self, *args: str) -> str:
        try:
            completed = subprocess.run(
                ["git", *args],
                cwd=self.workspace,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                timeout=10,
                check=False,
                env={key: value for key, value in os.environ.items() if key in {"PATH", "HOME", "TMPDIR", "LANG", "LC_ALL"}},
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise ValueError("WORKSPACE_GIT_UNAVAILABLE") from exc
        if completed.returncode != 0:
            raise ValueError("WORKSPACE_GIT_FAILED")
        return completed.stdout
