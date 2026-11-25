#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

# 1. 加载环境变量（如存在 .env）
if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  source "$ROOT_DIR/.env"
  set +a
fi

# 2. 固定使用 Homebrew OpenJDK 17
if [ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "==> 检查并启动 MySQL 服务..."
brew services start mysql >/dev/null 2>&1 || true

echo "==> 初始化数据库表结构（幂等执行）..."
mysql -u "${DB_USERNAME:-root}" ${DB_PASSWORD:+-p"$DB_PASSWORD"} < "$ROOT_DIR/sql/schema.sql"
mysql -u "${DB_USERNAME:-root}" ${DB_PASSWORD:+-p"$DB_PASSWORD"} < "$ROOT_DIR/sql/seed_data.sql"

echo "==> 启动 Python NLP 对照服务 (127.0.0.1:8001)..."
(cd "$ROOT_DIR/nlp-service" && .venv/bin/uvicorn app:app --host 127.0.0.1 --port 8001 > "$LOG_DIR/nlp-service.log" 2>&1 & echo $! > "$LOG_DIR/nlp-service.pid")

echo "==> 构建并启动 Spring Boot 后端 (127.0.0.1:${SERVER_PORT:-8088})..."
if [ ! -f "$ROOT_DIR/backend/target/emotion-agent-backend-1.0.0.jar" ]; then
  (cd "$ROOT_DIR/backend" && mvn -B package -DskipTests)
fi
(cd "$ROOT_DIR/backend" && java -jar target/emotion-agent-backend-1.0.0.jar > "$LOG_DIR/backend.log" 2>&1 & echo $! > "$LOG_DIR/backend.pid")

echo "==> 启动 Vue 3 前端开发服务 (127.0.0.1:5174)..."
(cd "$ROOT_DIR/frontend" && npm run dev > "$LOG_DIR/frontend.log" 2>&1 & echo $! > "$LOG_DIR/frontend.pid")

echo ""
echo "全部服务已启动："
echo "  - 前端页面：     http://127.0.0.1:5174"
echo "  - 后端 API：     http://127.0.0.1:${SERVER_PORT:-8088}/api/health"
echo "  - Python NLP：   http://127.0.0.1:8001/health"
echo "  - 演示学生账号： student / Student@123"
echo "  - 管理员账号：   admin / Admin@123"
echo "日志目录：$LOG_DIR（停止全部服务请执行 ./stop.sh）"
