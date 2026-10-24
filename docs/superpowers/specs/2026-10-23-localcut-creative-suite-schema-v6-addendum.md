# LocalCut Creative Suite — Schema v6 Addendum

Date: 2026-10-23

This addendum supersedes older schema-version statements in the 2026-10-23 Creative Suite spec/plan.

## Current contract

- `BuildContract.PROJECT_SCHEMA_VERSION = 6`.
- `ProjectJson.decode` accepts schema versions 1 through 6 and upgrades decoded projects to the current contract.
- Schema v4 introduced composition-graph persistence.
- Schema v5 introduced design elements, canvas settings and reusable components.
- Schema v6 introduced beat-marker persistence.
- Existing creative clip metadata (animations, compositing, effects, time-map and audio processing) must continue round-tripping under the v6 codec.

## Lock

Do not increment the project schema again merely to expose UI or complete an existing renderer/backend.

A future schema increment is allowed only when a genuinely new persisted field requires it and the same change includes:

1. complete encoder support;
2. complete decoder support;
3. deterministic defaults/migration for every supported legacy schema;
4. round-trip tests;
5. invalid/corrupt input tests;
6. reopen/persistence regression.

Tracking, stabilization and auto-reframe must not be described as fully persisted until their concrete metadata is actually encoded/decoded and covered by these gates.
