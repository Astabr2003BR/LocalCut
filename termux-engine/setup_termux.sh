#!/usr/bin/env bash
set -euo pipefail

ENGINE_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ENGINE_DIR/.." && pwd)"
STATE_DIR="$HOME/.localcut-engine"
LOG_FILE="$STATE_DIR/engine.log"
AUTOMATION_LOG="$STATE_DIR/automation.log"
START_SCRIPT="$STATE_DIR/start-localcut-engine"
AUTO_RECOVER_SCRIPT="$STATE_DIR/auto-recover"
REFRESH_ONLY=0
ENABLE_EXTERNAL_AUTOMATION=0

for arg in "$@"; do
  case "$arg" in
    --refresh-only) REFRESH_ONLY=1 ;;
    --enable-external-automation) ENABLE_EXTERNAL_AUTOMATION=1 ;;
    *) echo "ERRO: argumento desconhecido: $arg" >&2; exit 2 ;;
  esac
done

TERMUX_MODE=0
if [ -n "${TERMUX_VERSION:-}" ] || [[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]]; then
  TERMUX_MODE=1
fi

if command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="$(command -v python3)"
elif command -v python >/dev/null 2>&1; then
  PYTHON_BIN="$(command -v python)"
else
  if [ "$TERMUX_MODE" -eq 1 ] || command -v pkg >/dev/null 2>&1; then
    echo "ERRO: Python não encontrado. No Termux: pkg install -y python" >&2
  else
    echo "ERRO: Python não encontrado. Em Ubuntu/UserLAnd/Linux: apt update && apt install -y python3" >&2
  fi
  exit 1
fi

if [ "$TERMUX_MODE" -eq 1 ]; then
  ENVIRONMENT_LABEL="Termux"
else
  ENVIRONMENT_LABEL="Ubuntu/UserLAnd/Linux"
fi

mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR"

printf -v QUOTED_ROOT '%q' "$REPO_ROOT"
printf -v QUOTED_PYTHON '%q' "$PYTHON_BIN"
printf -v QUOTED_LOG '%q' "$LOG_FILE"
printf -v QUOTED_AUTOMATION_LOG '%q' "$AUTOMATION_LOG"

cat > "$START_SCRIPT" <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd $QUOTED_ROOT
mkdir -p "\$HOME/.localcut-engine"
chmod 700 "\$HOME/.localcut-engine"
nohup $QUOTED_PYTHON termux-engine/localcut_supervisor.py >> $QUOTED_LOG 2>&1 &
EOF
chmod 700 "$START_SCRIPT"

cat > "$AUTO_RECOVER_SCRIPT" <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd $QUOTED_ROOT
exec $QUOTED_PYTHON termux-engine/localcut_automation.py "\$@"
EOF
chmod 700 "$AUTO_RECOVER_SCRIPT"

if [ "$TERMUX_MODE" -eq 1 ]; then
  BOOT_DIR="$HOME/.termux/boot"
  BOOT_SCRIPT="$BOOT_DIR/localcut-engine"
  mkdir -p "$BOOT_DIR"
  chmod 700 "$BOOT_DIR"
  printf -v QUOTED_AUTO_RECOVER '%q' "$AUTO_RECOVER_SCRIPT"
  cat > "$BOOT_SCRIPT" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
set -u
termux-wake-lock >/dev/null 2>&1 || true
nohup bash $QUOTED_AUTO_RECOVER --daemon >> $QUOTED_AUTOMATION_LOG 2>&1 &
EOF
  chmod 700 "$BOOT_SCRIPT"

  if [ "$ENABLE_EXTERNAL_AUTOMATION" -eq 1 ]; then
    PROPERTIES_DIR="$HOME/.termux"
    PROPERTIES_FILE="$PROPERTIES_DIR/termux.properties"
    mkdir -p "$PROPERTIES_DIR"
    chmod 700 "$PROPERTIES_DIR"
    touch "$PROPERTIES_FILE"
    if grep -Eq '^[[:space:]#]*allow-external-apps[[:space:]]*=' "$PROPERTIES_FILE"; then
      sed -E -i 's/^[[:space:]#]*allow-external-apps[[:space:]]*=.*$/allow-external-apps = true/' "$PROPERTIES_FILE"
    else
      printf '\nallow-external-apps = true\n' >> "$PROPERTIES_FILE"
    fi
    chmod 600 "$PROPERTIES_FILE"
    if command -v termux-reload-settings >/dev/null 2>&1; then
      termux-reload-settings || true
    fi
  fi
fi

if [ "$REFRESH_ONLY" -eq 0 ]; then
  nohup bash "$AUTO_RECOVER_SCRIPT" --daemon >> "$AUTOMATION_LOG" 2>&1 &
fi

cat <<EOF
LocalCut Agent Engine configurado.
Ambiente: $ENVIRONMENT_LABEL
Python: $PYTHON_BIN
Launcher: $START_SCRIPT
Recuperação automática: $AUTO_RECOVER_SCRIPT
Boot script: ${BOOT_SCRIPT:-não aplicável}
Automação externa: $([ "$ENABLE_EXTERNAL_AUTOMATION" -eq 1 ] && echo habilitada || echo preservada)
Pareamento inicial/recuperação manual (se necessário):
  cd "$REPO_ROOT" && "$PYTHON_BIN" termux-engine/localcut_supervisor.py --pair
EOF
