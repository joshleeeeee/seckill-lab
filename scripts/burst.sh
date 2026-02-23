#!/usr/bin/env bash
set -euo pipefail

# 通用压测脚本。
# 用法：bash scripts/burst.sh <stage> [activityId] [requests] [concurrency] [baseUrl] [stock]
# 例如：bash scripts/burst.sh v2 1001 300 60

STAGE="${1:-}"
ACTIVITY_ID="${2:-1001}"
REQUESTS="${3:-300}"
CONCURRENCY="${4:-60}"
BASE_URL="${5:-http://localhost:8080}"
STOCK="${6:-50}"

if [[ -z "${STAGE}" ]]; then
  echo "缺少 stage 参数。示例：v0 / v1 / v2" >&2
  exit 1
fi

# 支持任意形如 v数字 的阶段名（例如 v0、v1、v2、v3 ...）。
if [[ ! "${STAGE}" =~ ^v[0-9]+$ ]]; then
  echo "不支持的 stage：${STAGE}（格式应为 v数字，例如 v2）" >&2
  exit 1
fi

# BURST_MODE:
# - basic：只跑并发并输出快照
# - latency：额外输出吞吐和延迟分位统计
# - auto：v0 用 basic，v1/v2 用 latency
MODE="${BURST_MODE:-auto}"
if [[ "${MODE}" == "auto" ]]; then
  if [[ "${STAGE}" == "v0" ]]; then
    MODE="basic"
  else
    MODE="latency"
  fi
fi

if [[ "${MODE}" != "basic" && "${MODE}" != "latency" ]]; then
  echo "不支持的 BURST_MODE：${MODE}（可选：basic / latency / auto）" >&2
  exit 1
fi

ACTIVITY_BASE_URL="${BASE_URL}/api/${STAGE}/activities/${ACTIVITY_ID}"

# 每轮前先重置活动，保证实验起点一致。
curl -s -X POST "${ACTIVITY_BASE_URL}/reset?stock=${STOCK}" >/dev/null

if [[ "${MODE}" == "latency" ]]; then
  # 将每次请求响应落盘到临时目录，用于汇总延迟指标。
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "${TMP_DIR}"' EXIT

  # 记录压测窗口开始时间，用于计算吞吐。
  START_MS="$(date +%s%3N)"

  # 并发发送请求，并在响应末尾附加 HTTP 状态与耗时元数据。
  seq 1 "${REQUESTS}" | xargs -I{} -P "${CONCURRENCY}" \
    bash -c 'curl -s -w "\n__META__ %{http_code} %{time_total}\n" -X POST "${0}/api/${1}/activities/${2}/attempt" -H "Content-Type: application/json" -d "{\"userId\":\"u${3}\"}" > "${4}/${3}.resp"' \
    "${BASE_URL}" "${STAGE}" "${ACTIVITY_ID}" "{}" "${TMP_DIR}"

  # 记录压测窗口结束时间。
  END_MS="$(date +%s%3N)"

  # 汇总请求结果，计算延迟分位与吞吐指标。
  python3 - "${TMP_DIR}" "${REQUESTS}" "${START_MS}" "${END_MS}" "${STAGE}" <<'PY'
import json
import math
import pathlib
import sys


def percentile(values, p):
    if not values:
        return 0.0
    index = max(0, math.ceil(p / 100 * len(values)) - 1)
    return values[index]


resp_dir = pathlib.Path(sys.argv[1])
expected = int(sys.argv[2])
start_ms = int(sys.argv[3])
end_ms = int(sys.argv[4])
stage = sys.argv[5]

latencies_ms = []
http_status_count = {}
api_success = 0
api_fail = 0
malformed_count = 0

for path in sorted(resp_dir.glob("*.resp")):
    content = path.read_text(encoding="utf-8")
    marker = "\n__META__ "
    if marker not in content:
        malformed_count += 1
        continue

    body, meta = content.rsplit(marker, 1)
    meta_parts = meta.strip().split()
    if len(meta_parts) != 2:
        malformed_count += 1
        continue

    status_code = meta_parts[0]
    elapsed_ms = float(meta_parts[1]) * 1000
    latencies_ms.append(elapsed_ms)
    http_status_count[status_code] = http_status_count.get(status_code, 0) + 1

    try:
        # 解析标准 ApiResponse，并统计业务成功与失败次数。
        obj = json.loads(body.strip())
        if isinstance(obj, dict) and obj.get("success") is True:
            api_success += 1
        else:
            api_fail += 1
    except json.JSONDecodeError:
        api_fail += 1

latencies_ms.sort()
actual = len(latencies_ms)
duration_ms = max(1, end_ms - start_ms)
rps = actual * 1000 / duration_ms

print(f"=== {stage} 压测延迟汇总 ===")
print(f"期望请求数: {expected}")
print(f"采集响应数: {actual}")
print(f"业务成功数: {api_success}")
print(f"业务失败数: {api_fail}")
print(f"异常采集数: {malformed_count}")
print(f"压测窗口毫秒: {duration_ms}")
print(f"吞吐量(请求/秒): {rps:.2f}")

if actual > 0:
    print(f"延迟P50(毫秒): {percentile(latencies_ms, 50):.2f}")
    print(f"延迟P95(毫秒): {percentile(latencies_ms, 95):.2f}")
    print(f"延迟P99(毫秒): {percentile(latencies_ms, 99):.2f}")
    print(f"最大延迟(毫秒): {latencies_ms[-1]:.2f}")

if http_status_count:
    print("HTTP状态码统计:")
    for code in sorted(http_status_count):
        print(f"  {code}: {http_status_count[code]}")

print("=== 延迟汇总结束 ===")
PY

  echo
fi

echo "=== ${STAGE} 快照 ==="

# 输出最终快照，查看正确性指标。
curl -s "${ACTIVITY_BASE_URL}/snapshot" | python3 -m json.tool
