#!/usr/bin/env bash
set -euo pipefail

: "${RECALL_API_KEY:?Set RECALL_API_KEY before running this benchmark}"

URL="${RECALL_URL:-https://recall.abhiroop.dev/v1/mcp}"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
PRIMARY_APP_ID="recall-benchmark-${RUN_ID}-a"
CONTROL_APP_ID="recall-benchmark-${RUN_ID}-b"
RESULTS="${BENCHMARK_RESULTS:-/tmp/recall-benchmark-${RUN_ID}.csv}"
CREATED_IDS=()
CALL_RESPONSE=""

printf 'operation,sequence,dns,tcp,tls,ttfb,total,http_status\n' > "$RESULTS"

tool_call() {
    local operation="$1"
    local sequence="$2"
    local payload="$3"
    local response timing

    response="$(mktemp)"
    timing="$(curl --silent --show-error --fail --max-time 30 \
        --output "$response" \
        --write-out '%{time_namelookup},%{time_connect},%{time_appconnect},%{time_starttransfer},%{time_total},%{http_code}' \
        --request POST "$URL" \
        --header "Authorization: Bearer $RECALL_API_KEY" \
        --header 'Content-Type: application/json' \
        --header 'Accept: application/json, text/event-stream' \
        --data "$payload")"
    CALL_RESPONSE="$(<"$response")"
    rm "$response"

    jq -e '.result and (.result.isError | not)' <<< "$CALL_RESPONSE" > /dev/null
    printf '%s,%s,%s\n' "$operation" "$sequence" "$timing" >> "$RESULTS"
}

save_memory() {
    local operation="$1"
    local sequence="$2"
    local app_id="$3"
    local text="$4"
    local payload id

    payload="$(jq -cn --arg appId "$app_id" --arg text "$text" --argjson id "$sequence" \
        '{jsonrpc:"2.0", id:$id, method:"tools/call", params:{name:"saveMemory", arguments:{dto:{appId:$appId, text:$text}}}}')"
    tool_call "$operation" "$sequence" "$payload"
    id="$(jq -er '.result.content[0].text | fromjson | .id' <<< "$CALL_RESPONSE")"
    CREATED_IDS+=("$id")
}

cleanup() {
    local id app_id payload response cleanup_failed=0
    set +e

    for id in "${CREATED_IDS[@]}"; do
        payload="$(jq -cn --arg id "$id" '{jsonrpc:"2.0", id:999, method:"tools/call", params:{name:"deleteMemory", arguments:{id:$id}}}')"
        curl --silent --show-error --fail --max-time 30 --output /dev/null \
            --request POST "$URL" \
            --header "Authorization: Bearer $RECALL_API_KEY" \
            --header 'Content-Type: application/json' \
            --header 'Accept: application/json, text/event-stream' \
            --data "$payload" || cleanup_failed=1
    done

    for app_id in "$PRIMARY_APP_ID" "$CONTROL_APP_ID"; do
        payload="$(jq -cn --arg appId "$app_id" '{jsonrpc:"2.0", id:1000, method:"tools/call", params:{name:"getMemories", arguments:{appId:$appId}}}')"
        response="$(curl --silent --show-error --fail --max-time 30 \
            --request POST "$URL" \
            --header "Authorization: Bearer $RECALL_API_KEY" \
            --header 'Content-Type: application/json' \
            --header 'Accept: application/json, text/event-stream' \
            --data "$payload")" || cleanup_failed=1
        jq -e '(.result.isError | not) and (.result.content[0].text | fromjson |
            if type == "array" then . == []
            elif type == "object" then .memories == [] and has("nextCursor") and .nextCursor == null
            else false end)' <<< "$response" > /dev/null || cleanup_failed=1
        printf 'cleanup contract: %s\n' "$(jq -r '.result.content[0].text | fromjson | type' <<< "$response")"
    done
    set -e
    return "$cleanup_failed"
}

trap cleanup EXIT

for sequence in {1..5}; do
    save_memory warm-save "$sequence" "$PRIMARY_APP_ID" "Warm-up memory $sequence for benchmark $RUN_ID."
done

for sequence in {1..30}; do
    save_memory save "$sequence" "$PRIMARY_APP_ID" "Primary benchmark memory $sequence for run $RUN_ID uses Gradle and Java 25."
done

save_memory control 1 "$CONTROL_APP_ID" "Primary benchmark memory 1 for run $RUN_ID uses Gradle and Java 25."

for sequence in {1..30}; do
    payload="$(jq -cn --arg appId "$PRIMARY_APP_ID" --arg text "Which primary benchmark memory for run $RUN_ID uses Gradle and Java 25?" --argjson id "$sequence" \
        '{jsonrpc:"2.0", id:$id, method:"tools/call", params:{name:"getTopNClosest", arguments:{appId:$appId, text:$text, topN:1}}}')"
    tool_call retrieval "$sequence" "$payload"
    jq -e --arg appId "$PRIMARY_APP_ID" '(.result.content[0].text | fromjson) as $results | ($results | length == 1) and $results[0].appId == $appId' <<< "$CALL_RESPONSE" > /dev/null
done

cleanup
trap - EXIT
printf 'Cleanup verified for both temporary namespaces (%s records).\n' "${#CREATED_IDS[@]}"

python3 - "$RESULTS" <<'PY'
import csv
import math
import statistics
import sys

rows = list(csv.DictReader(open(sys.argv[1], newline="")))
for operation in ("save", "retrieval"):
    totals = sorted(float(row["total"]) for row in rows if row["operation"] == operation)
    def percentile(percent):
        return totals[math.ceil(percent * len(totals)) - 1]
    print(f"{operation}: n={len(totals)} mean={statistics.mean(totals):.3f}s p50={percentile(.50):.3f}s p95={percentile(.95):.3f}s p99={percentile(.99):.3f}s min={totals[0]:.3f}s max={totals[-1]:.3f}s")
print(f"results={sys.argv[1]}")
PY
