#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ENGINE_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ENGINE_DIR/.." && pwd)"
STATE_DIR="$HOME/.localcut-engine"
BOOT_DIR="$HOME/.termux/boot"
BOOT_SCRIPT="$BOOT_DIR/localcut-engine"
LOG_FILE="$STATE_DIR/engine.log"

command -v python >/dev/null 2>&1 || {
  echo "ERRO: Python não encontrado. Instale no Termux com: pkg install python" >&2
  exit 1
}

mkdir -p "$STATE_DIR" "$BOOT_DIR"
chmod 700 "$STATE_DIR" "$BOOT_DIR"

if ! command -v ffprobe >/dev/null 2>&1; then
  echo "AVISO: ffprobe não encontrado. ANALYZE_MEDIA por mídia exige: pkg install ffmpeg" >&2
fi

QUOTED_ROOT="$(printf '%q' "$REPO_ROOT")"
cat > "$BOOT_SCRIPT" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
set -u
cd $QUOTED_ROOT || exit 1
mkdir -p "\$HOME/.localcut-engine"
chmod 700 "\$HOME/.localcut-engine"
nohup python termux-engine/localcut_supervisor.py >> "\$HOME/.localcut-engine/engine.log" 2>&1 &
EOF
chmod 700 "$BOOT_SCRIPT"

cat <<EOF
LocalCut Termux Agent Engine configurado.

Primeiro pareamento (uma vez):
  cd $REPO_ROOT
  python termux-engine/localcut_supervisor.py --pair

Depois do pareamento, inicialização normal:
  cd $REPO_ROOT
  nohup python termux-engine/localcut_supervisor.py >> $LOG_FILE 2>&1 &

Termux:Boot launcher:
  $BOOT_SCRIPT
EOF
