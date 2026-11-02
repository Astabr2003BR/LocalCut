# Generated media retention / GC plan — 2026-10-28

## Goal
Reclaim orphaned LocalCut-generated Reverse MP4 files without breaking the current project, undo/redo, imports, or in-flight generated media.

## Safety model

1. GC is disabled whenever the editor session reports `canUndo` or `canRedo`.
2. Only files matching the exact LocalCut Reverse namespace are candidates: `reverse-<uuid>-<uuid>.mp4`.
3. Shared/imported media (`shared-*`) and every other filename are never candidates.
4. A generated file referenced by any Asset in the current Project is protected.
5. Candidates younger than the configured grace period are protected from cleanup.
6. Symlinks, directories, empty/non-regular entries, path escapes, malformed provider URIs, and unexpected authority/path forms are fail-closed.
7. Cleanup runs as a bounded directory scan. It never recursively traverses storage.
8. Failure to delete an orphan is reported but never causes deletion of another non-candidate file.

## Why history is gated rather than introspected
The build uses a packaged base source plus `overrides/`; the concrete `HistoryManager` implementation is not available as a standalone override file. Overwriting an unseen history implementation would be unsafe. `ProjectSessionState.canUndo/canRedo` is already public and authoritative for whether transient history is reachable. Therefore cleanup only runs when both stacks are unavailable.

After process restart, undo/redo history is not exposed by the current session; the current Project remains the source of persisted references. Current-project Assets are always protected.

## Implementation steps

1. Add pure retention-policy tests first (RED): generated-name recognition, provider URI parsing, history gate, reference protection, age boundary.
2. Implement `GeneratedMediaRetentionPolicy`.
3. Add collector tests first (RED): bounded top-level scan, candidate deletion, protected/non-candidate preservation, failed deletion reporting.
4. Implement `GeneratedMediaGarbageCollector` using the existing `remote-media` directory.
5. Expose a runtime cleanup method that accepts current project + `canUndo/canRedo` and runs on IO.
6. Wire cleanup only at safe lifecycle points; do not scan on every editor action.
7. Add/update checkpoint documentation.
8. Give the resulting new HEAD exactly one CI status query. Never poll the same SHA.
