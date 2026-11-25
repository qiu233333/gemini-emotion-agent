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

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
SERVER_PORT="${SERVER_PORT:-8088}"

# 避免重复启动残留旧进程
if [ -x "$ROOT_DIR/stop.sh" ]; then
  "$ROOT_DIR/stop.sh" >/dev/null 2>&1 || true
fi

echo "==> 检查并启动 MySQL 服务..."
brew services start mysql >/dev/null 2>&1 || true

echo "==> 等待 MySQL (${DB_HOST}:${DB_PORT}) 就绪..."
MYSQL_READY=0
for i in $(seq 1 30); do
  if mysqladmin --protocol=TCP -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} ping --silent >/dev/null 2>&1; then
    MYSQL_READY=1
    break
  fi
  sleep 1
done

if [ "$MYSQL_READY" -ne 1 ]; then
  echo "❌ MySQL 在 30 秒内未能就绪，请运行 'brew services list' 或检查 /opt/homebrew/var/mysql/*.err"
  exit 1
fi

echo "==> 初始化数据库表结构（幂等执行）..."
mysql --protocol=TCP -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} < "$ROOT_DIR/sql/schema.sql"
mysql --protocol=TCP -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} < "$ROOT_DIR/sql/seed_data.sql"

echo "==> 启动 Python NLP 对照服务 (127.0.0.1:8001)..."
(cd "$ROOT_DIR/nlp-service" && nohup .venv/bin/uvicorn app:app --host 127.0.0.1 --port 8001 </dev/null > "$LOG_DIR/nlp-service.log" 2>&1 & echo $! > "$LOG_DIR/nlp-service.pid")

echo "==> 构建并启动 Spring Boot 后端 (127.0.0.1:${SERVER_PORT})..."
if [ ! -f "$ROOT_DIR/backend/target/emotion-agent-backend-1.0.0.jar" ]; then
  (cd "$ROOT_DIR/backend" && mvn -B package -DskipTests)
fi
(cd "$ROOT_DIR/backend" && nohup java -jar target/emotion-agent-backend-1.0.0.jar </dev/null > "$LOG_DIR/backend.log" 2>&1 & echo $! > "$LOG_DIR/backend.pid")

echo "==> 启动 Vue 3 前端开发服务 (127.0.0.1:5174)..."
if [ ! -d "$ROOT_DIR/frontend/node_modules" ]; then
  (cd "$ROOT_DIR/frontend" && npm install)
fi
(cd "$ROOT_DIR/frontend" && nohup ./node_modules/.bin/vite --host 127.0.0.1 --port 5174 </dev/null > "$LOG_DIR/frontend.log" 2>&1 & echo $! > "$LOG_DIR/frontend.pid")

echo "==> 等待前后端服务就绪..."
for i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/health" >/dev/null 2>&1 \
     && curl -fsS "http://127.0.0.1:5174/" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo ""
echo "全部服务已启动："
echo "  - 前端页面：     http://127.0.0.1:5174"
echo "  - 后端 API：     http://127.0.0.1:${SERVER_PORT}/api/health"
echo "  - Python NLP：   http://127.0.0.1:8001/health"
echo "  - 演示学生账号： student / Student@123"
echo "  - 管理员账号：   admin / Admin@123"
echo "日志目录：${LOG_DIR}（停止全部服务请执行 ./stop.sh）"
