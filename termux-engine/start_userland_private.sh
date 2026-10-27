#!/usr/bin/env bash
set -euo pipefail
ENGINE_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ENGINE_DIR/.." && pwd)"
STATE_DIR="$HOME/.localcut-engine"
PYTHON_BIN="$(command -v python3 || command -v python || true)"
PAIR=0
if [ "${1:-}" = "--pair" ]; then PAIR=1; fi
if [ -z "$PYTHON_BIN" ]; then echo "ERRO: Python 3 não encontrado" >&2; exit 1; fi
mkdir -p "$STATE_DIR"; chmod 700 "$STATE_DIR"

stop_owned() {
  local pid_file="$1" needle="$2"
  [ -f "$pid_file" ] || return 0
  local pid; pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ "$pid" =~ ^[0-9]+$ ]] && [ -r "/proc/$pid/cmdline" ]; then
    if tr '\0' ' ' < "/proc/$pid/cmdline" | grep -Fq "$needle"; then
      kill "$pid" 2>/dev/null || true
      sleep 1
    fi
  fi
  rm -f "$pid_file"
}

seed_private_ip_from_previous_log() {
  local state_file="$STATE_DIR/private-ipv4"
  [ -s "$state_file" ] && return 0
  [ -f "$STATE_DIR/private-proxy.log" ] || return 0
  local previous_host
  previous_host="$(
    sed -n 's/^LOCALCUT_PRIVATE_PROXY_READY=\([^:,]*\):.*/\1/p' "$STATE_DIR/private-proxy.log" \
      | tail -n 1
  )"
  if [ -n "$previous_host" ]; then
    printf '%s\n' "$previous_host" > "$state_file"
    chmod 600 "$state_file"
  fi
}

stop_owned "$STATE_DIR/private-proxy.pid" "localcut_private_proxy.py"
stop_owned "$STATE_DIR/engine.pid" "localcut_supervisor.py"
seed_private_ip_from_previous_log

cd "$REPO_ROOT"
ENGINE_ARGS=()
if [ "$PAIR" -eq 1 ]; then ENGINE_ARGS+=(--pair); fi
nohup "$PYTHON_BIN" termux-engine/localcut_supervisor.py "${ENGINE_ARGS[@]}" \
  > "$STATE_DIR/engine.log" 2>&1 < /dev/null &
echo $! > "$STATE_DIR/engine.pid"

sleep 1
nohup "$PYTHON_BIN" termux-engine/localcut_private_proxy.py \
  --state-file "$STATE_DIR/private-ipv4" \
  > "$STATE_DIR/private-proxy.log" 2>&1 < /dev/null &
echo $! > "$STATE_DIR/private-proxy.pid"

sleep 2
for name in engine private-proxy; do
  pid="$(cat "$STATE_DIR/$name.pid")"
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "ERRO: $name não iniciou" >&2
    cat "$STATE_DIR/$name.log" >&2 || true
    exit 1
  fi
done

if [ "$PAIR" -eq 1 ]; then
  grep -E 'LOCALCUT_PAIRING_CODE|LOCALCUT_ENGINE_READY' "$STATE_DIR/engine.log" || true
fi
grep -E 'LOCALCUT_PRIVATE_PROXY_READY' "$STATE_DIR/private-proxy.log" || true
