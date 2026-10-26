# LocalCut Termux Agent Engine

The Termux Agent Engine is an auxiliary LocalCut worker. It is not a second editor and does not expose a shell endpoint.

## First pairing

Install Python in Termux, enter the repository engine directory, then open a short-lived pairing window:

```bash
pkg install python
cd /path/to/LocalCut/termux-engine
python localcut_engine.py --pair
```

The engine prints only a one-time code such as:

```text
LOCALCUT_PAIRING_CODE=ABCD-EFGH-JKLM
```

Enter that code in LocalCut when the pairing UI is available. The LocalCut client exchanges it locally on `127.0.0.1`, stores the issued token in Android Keystore, and immediately verifies the connection with `PING`.

Pairing properties:

- code length: 12 characters from a human-safe alphabet;
- default validity: 5 minutes;
- maximum invalid attempts: 5;
- successful pairing invalidates the code immediately;
- the long-lived engine token is never printed by the normal CLI flow;
- engine credentials are persisted under `~/.localcut-engine/credentials.json` with private permissions.

## Normal start after pairing

```bash
python localcut_engine.py
```

The engine binds only to `127.0.0.1:43127`. Authenticated V1 operations remain limited to `PING`, `GET_STATUS`, and `GET_CAPABILITIES`. Arbitrary shell execution is not exposed.
