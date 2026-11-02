#!/usr/bin/env bash
set -euo pipefail

STATE_DIR="$HOME/.localcut-engine"
LOCK_FILE="$STATE_DIR/supervisor.lock"
START_SCRIPT="$STATE_DIR/start-localcut-engine"

if [ ! -x "$START_SCRIPT" ]; then
  echo "ERRO: launcher do LocalCut Agent Engine não encontrado. Execute setup_termux.sh primeiro." >&2
  exit 1
fi

if [ -r "$LOCK_FILE" ]; then
  PID="$(cat "$LOCK_FILE" 2>/dev/null || true)"
  case "$PID" in
    ''|*[!0-9]*)
      ;;
    *)
      if kill -0 "$PID" 2>/dev/null; then
        CMDLINE="$(tr '\0' ' ' < "/proc/$PID/cmdline" 2>/dev/null || true)"
        case "$CMDLINE" in
          *"termux-engine/localcut_supervisor.py"*)
            kill "$PID"
            for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do
              if ! kill -0 "$PID" 2>/dev/null; then
                break
              fi
              sleep 0.1
            done
            if kill -0 "$PID" 2>/dev/null; then
              echo "ERRO: o LocalCut Agent Engine antigo não encerrou de forma limpa." >&2
              exit 1
            fi
            ;;
          *)
            echo "ERRO: o PID salvo não pertence ao LocalCut Agent Engine; reinício cancelado por segurança." >&2
            exit 1
            ;;
        esac
      fi
      ;;
  esac
fi

exec bash "$START_SCRIPT"
