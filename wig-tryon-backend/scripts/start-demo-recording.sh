#!/usr/bin/env bash
# 录屏演示一键启动：GPT Image 2 / Doubao / Flux(→flux-1.1-pro) / Qwen万象(百炼)
# 用法：cd wig-tryon-backend && ./scripts/start-demo-recording.sh

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ENV_FILE="$ROOT/data/demo-recording.env"
if [[ ! -f "$ENV_FILE" ]]; then
  cat > "$ENV_FILE" <<'EOF'
# 录屏演示专用（data/ 已在 .gitignore，不会进 Git）
# 云雾 Key：GPT Image 2 / Flux 等
YUNWU_API_KEY=在此填入你的云雾Key
# 火山方舟 Key：小程序选「Doubao Seedream 5.0」时直连 doubao-seedream-5-0-260128
ARK_API_KEY=在此填入你的火山方舟Key
# 百炼 Key：小程序选「Qwen 万象」时走 DashScope 直连
DASHSCOPE_API_KEY=在此填入你的百炼Key
AI_MODE=real
AI_STRICT_REAL=false
AI_TRYON_PROVIDER=yunwu
WIG_ADMIN_ENABLED=true
WIG_ADMIN_TOKEN=demo-admin
YUNWU_TIMEOUT_SECONDS=180
EOF
  echo "已生成 $ENV_FILE — 请填入 YUNWU_API_KEY 后重新运行"
  exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

if [[ -z "${YUNWU_API_KEY:-}" || "$YUNWU_API_KEY" == *"在此填入"* ]]; then
  echo "请在 $ENV_FILE 中配置 YUNWU_API_KEY"
  exit 1
fi
if [[ -z "${DASHSCOPE_API_KEY:-}" ]]; then
  echo "请在 $ENV_FILE 中配置 DASHSCOPE_API_KEY（Qwen 万象必需）"
  exit 1
fi

echo "=== 录屏演示后端 ==="
echo "  Doubao Seedream 5.0 → 火山方舟 ARK_API_KEY"
echo "  GPT Image 2 / Flux 2 Max → 云雾或百炼（见模型路由）"
echo "  Qwen 万象 → 百炼 DashScope"
echo "  strict-real=false（单模型失败降级 seed，不中断录屏）"
echo ""

exec mvn -q spring-boot:run \
  -Dspring-boot.run.profiles=demo-recording \
  -Dspring-boot.run.jvmArguments="-DAPP_STORAGE_PUBLIC_BASE_URL=http://localhost:8080/files"
