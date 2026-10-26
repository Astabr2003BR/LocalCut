# LocalCut Termux Agent Engine

Phase V1 is a read-only local worker contract for LocalCut. It is not a second editor and does not expose a shell endpoint.

## Run in Termux

```bash
pkg install python
cd /path/to/LocalCut/termux-engine
export LOCALCUT_ENGINE_TOKEN="<paired-token>"
python localcut_engine.py
```

Generate a bootstrap token explicitly when needed:

```bash
python localcut_engine.py --generate-token
```

The engine binds to `127.0.0.1:43127`. V1 accepts only `PING`, `GET_STATUS`, and `GET_CAPABILITIES`.
