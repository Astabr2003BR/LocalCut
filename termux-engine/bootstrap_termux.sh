#!/usr/bin/env bash
set -euo pipefail

ENGINE_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ENGINE_DIR/.." && pwd)"
STATE_DIR="$HOME/.localcut-engine"
AUTO_RECOVER="$STATE_DIR/auto-recover"
AUTOMATION_LOG="$STATE_DIR/automation.log"

cd "$REPO_ROOT"
bash termux-engine/setup_termux.sh --refresh-only --enable-external-automation
bash "$AUTO_RECOVER" --once --force-restart
nohup bash "$AUTO_RECOVER" --daemon >> "$AUTOMATION_LOG" 2>&1 &

echo "LocalCut Agent Engine: automação habilitada."
