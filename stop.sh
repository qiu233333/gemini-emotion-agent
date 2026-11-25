#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"

for name in frontend backend nlp-service; do
  PID_FILE="$LOG_DIR/$name.pid"
  if [ -f "$PID_FILE" ]; then
    PID="$(cat "$PID_FILE")"
    if kill -0 "$PID" 2>/dev/null; then
      kill "$PID" && echo "Stopped $name (PID $PID)"
    fi
    rm -f "$PID_FILE"
  fi
done
