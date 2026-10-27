#!/usr/bin/env bash
set -euo pipefail

ENGINE_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ENGINE_DIR/.." && pwd)"
STATE_DIR="$HOME/.localcut-engine"
LOG_FILE="$STATE_DIR/engine.log"
START_SCRIPT="$STATE_DIR/start-localcut-engine"

if command -v python >/dev/null 2>&1; then
  PYTHON_BIN="$(command -v python)"
elif command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="$(command -v python3)"
else
  if command -v pkg >/dev/null 2>&1; then
    echo "ERRO: Python não encontrado. Instale com: pkg install python" >&2
  elif command -v apt-get >/dev/null 2>&1; then
    echo "ERRO: Python não encontrado. Instale com: apt update && apt install -y python3" >&2
  else
    echo "ERRO: Python não encontrado. Instale Python 3 e execute novamente." >&2
  fi
  exit 1
fi

mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR"

if ! command -v ffprobe >/dev/null 2>&1; then
  if command -v pkg >/dev/null 2>&1; then
    echo "AVISO: ffprobe não encontrado. ANALYZE_MEDIA exige: pkg install ffmpeg" >&2
  elif command -v apt-get >/dev/null 2>&1; then
    echo "AVISO: ffprobe não encontrado. ANALYZE_MEDIA exige: apt update && apt install -y ffmpeg" >&2
  else
    echo "AVISO: ffprobe não encontrado. Instale FFmpeg para usar ANALYZE_MEDIA." >&2
  fi
fi

printf -v QUOTED_ROOT '%q' "$REPO_ROOT"
printf -v QUOTED_PYTHON '%q' "$PYTHON_BIN"
printf -v QUOTED_LOG '%q' "$LOG_FILE"

cat > "$START_SCRIPT" <<EOF
#!/usr/bin/env bash
set -u
cd $QUOTED_ROOT || exit 1
mkdir -p "\$HOME/.localcut-engine"
chmod 700 "\$HOME/.localcut-engine"
nohup $QUOTED_PYTHON termux-engine/localcut_supervisor.py >> $QUOTED_LOG 2>&1 &
EOF
chmod 700 "$START_SCRIPT"

TERMUX_MODE=0
if [ -n "${TERMUX_VERSION:-}" ] || [[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]]; then
  TERMUX_MODE=1
fi

if [ "$TERMUX_MODE" -eq 1 ]; then
  BOOT_DIR="$HOME/.termux/boot"
  BOOT_SCRIPT="$BOOT_DIR/localcut-engine"
  mkdir -p "$BOOT_DIR"
  chmod 700 "$BOOT_DIR"
  printf -v QUOTED_START '%q' "$START_SCRIPT"
  cat > "$BOOT_SCRIPT" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
set -u
bash $QUOTED_START
EOF
  chmod 700 "$BOOT_SCRIPT"
  MODE_LABEL="Termux"
  AUTO_START_INFO="Termux:Boot launcher: $BOOT_SCRIPT"
else
  MODE_LABEL="Ubuntu/UserLAnd/Linux"
  AUTO_START_INFO="Launcher Linux criado em: $START_SCRIPT"
fi

cat <<EOF
LocalCut Agent Engine configurado para: $MODE_LABEL
Python: $PYTHON_BIN

Primeiro pareamento (uma vez):
  cd $REPO_ROOT
  $PYTHON_BIN termux-engine/localcut_supervisor.py --pair

Depois do pareamento, inicialização normal:
  $START_SCRIPT

$AUTO_START_INFO
EOF
