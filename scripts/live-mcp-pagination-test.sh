#!/usr/bin/env bash
set -euo pipefail

: "${RECALL_API_KEY:?Set RECALL_API_KEY before running this test}"

URL="${RECALL_URL:-https://recall.abhiroop.dev/v1/mcp}"
PROJECT_ID="${RECALL_GCP_PROJECT_ID:-recall-25902}"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
APP_ID="recall-pagination-${RUN_ID}"
EMPTY_APP_ID="${APP_ID}-empty"
CREATED_IDS=()
OWNED_IDS=()
CALL_RESPONSE=""

mcp_call() {
    local payload="$1" response

    response="$(mktemp)"
    curl --silent --show-error --fail --max-time 30 --output "$response" \
        --request POST "$URL" \
        --header "Authorization: Bearer $RECALL_API_KEY" \
        --header 'Content-Type: application/json' \
        --header 'Accept: application/json, text/event-stream' \
        --data "$payload"
    CALL_RESPONSE="$(<"$response")"
    rm "$response"
    if ! jq -e '.jsonrpc == "2.0" and (.error | not) and .result and (.result.isError | not)' \
        <<< "$CALL_RESPONSE" > /dev/null; then
        printf 'MCP tool call failed: %s\n' \
            "$(jq -c '{error, result: (.result | {isError})}' <<< "$CALL_RESPONSE")" >&2
        return 1
    fi
}

get_page() {
    local app_id="$1" cursor="${2:-}" payload

    if [[ -n "$cursor" ]]; then
        payload="$(jq -cn --arg appId "$app_id" --argjson cursor "$cursor" \
            '{jsonrpc:"2.0", id:1, method:"tools/call", params:{name:"getMemories", arguments:{appId:$appId, cursor:$cursor}}}')"
    else
        payload="$(jq -cn --arg appId "$app_id" \
            '{jsonrpc:"2.0", id:1, method:"tools/call", params:{name:"getMemories", arguments:{appId:$appId}}}')"
    fi
    mcp_call "$payload"
    if ! jq -ce '.result.content[0].text | fromjson |
        select(type == "object" and has("memories") and has("nextCursor"))' <<< "$CALL_RESPONSE"; then
        printf 'getMemories for %s did not return the required page object.\n' "$app_id" >&2
        return 1
    fi
}

delete_memory() {
    local id="$1" payload

    payload="$(jq -cn --arg id "$id" \
        '{jsonrpc:"2.0", id:2, method:"tools/call", params:{name:"deleteMemory", arguments:{id:$id}}}')"
    mcp_call "$payload"
}

cleanup() {
    local id page cleanup_failed=0
    set +e

    for id in "${CREATED_IDS[@]}"; do
        delete_memory "$id" || cleanup_failed=1
    done
    for page in "$(get_page "$APP_ID")" "$(get_page "$EMPTY_APP_ID")"; do
        jq -e '.memories == [] and .nextCursor == null' <<< "$page" > /dev/null || cleanup_failed=1
    done

    set -e
    return "$cleanup_failed"
}

on_exit() {
    local status=$?
    if ! cleanup; then
        status=1
    fi
    exit "$status"
}

trap on_exit EXIT

ADC_TOKEN="$(gcloud --quiet auth application-default print-access-token)"
CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%S.123456789Z)"

# Generate and retain all intended IDs before any write; only successful creates enter CREATED_IDS.
for _ in {1..21}; do
    OWNED_IDS+=("pagination-${RUN_ID}-$(openssl rand -hex 12)")
done
EXPECTED_IDS="$(printf '%s\n' "${OWNED_IDS[@]}" | LC_ALL=C sort | jq -Rsc 'split("\n")[:-1]')"

EMPTY_PAGE="$(get_page "$EMPTY_APP_ID")"
jq -e '.memories == [] and .nextCursor == null' <<< "$EMPTY_PAGE" > /dev/null

for id in "${OWNED_IDS[@]}"; do
    document="$(jq -cn --arg appId "$APP_ID" --arg id "$id" --arg createdAt "$CREATED_AT" \
        '{fields:{appId:{stringValue:$appId}, text:{stringValue:("Pagination fixture " + $id)}, createdAt:{timestampValue:$createdAt}}}')"
    curl --silent --show-error --fail --max-time 30 --output /dev/null \
        --request POST "https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/memories?documentId=${id}" \
        --header "Authorization: Bearer $ADC_TOKEN" \
        --header 'Content-Type: application/json' \
        --data "$document"
    CREATED_IDS+=("$id")
done

PAGE_1="$(get_page "$APP_ID")"
jq -e --argjson expected "$EXPECTED_IDS" --arg appId "$APP_ID" '
    (.memories | length) == 10 and .nextCursor != null and
    (.memories | map(.id)) == $expected[0:10] and
    (.memories | all(.[]; .appId == $appId))' <<< "$PAGE_1" > /dev/null
CURSOR_1="$(jq -ce '.nextCursor' <<< "$PAGE_1")"

PAGE_2="$(get_page "$APP_ID" "$CURSOR_1")"
jq -e --argjson expected "$EXPECTED_IDS" --arg appId "$APP_ID" '
    (.memories | length) == 10 and .nextCursor != null and
    (.memories | map(.id)) == $expected[10:20] and
    (.memories | all(.[]; .appId == $appId))' <<< "$PAGE_2" > /dev/null
CURSOR_2="$(jq -ce '.nextCursor' <<< "$PAGE_2")"

PAGE_3="$(get_page "$APP_ID" "$CURSOR_2")"
jq -e --argjson expected "$EXPECTED_IDS" --arg appId "$APP_ID" '
    (.memories | length) == 1 and .nextCursor == null and
    (.memories | map(.id)) == $expected[20:21] and
    (.memories | all(.[]; .appId == $appId))' <<< "$PAGE_3" > /dev/null
jq -s --argjson expected "$EXPECTED_IDS" '
    map(.memories | map(.id)) | add as $ids |
    ($ids | length) == 21 and ($ids | unique | length) == 21 and $ids == $expected' \
    <<< "$PAGE_1"$'\n'"$PAGE_2"$'\n'"$PAGE_3" > /dev/null

LAST_ID="$(jq -r '.memories[0].id' <<< "$PAGE_3")"
delete_memory "$LAST_ID"
REMAINING_IDS=()
for id in "${CREATED_IDS[@]}"; do
    [[ "$id" == "$LAST_ID" ]] || REMAINING_IDS+=("$id")
done
CREATED_IDS=("${REMAINING_IDS[@]}")

MULTIPLE_PAGE_1="$(get_page "$APP_ID")"
MULTIPLE_CURSOR_1="$(jq -ce '.nextCursor' <<< "$MULTIPLE_PAGE_1")"
MULTIPLE_PAGE_2="$(get_page "$APP_ID" "$MULTIPLE_CURSOR_1")"
MULTIPLE_CURSOR_2="$(jq -ce '.nextCursor' <<< "$MULTIPLE_PAGE_2")"
MULTIPLE_PAGE_3="$(get_page "$APP_ID" "$MULTIPLE_CURSOR_2")"
jq -s --argjson expected "$EXPECTED_IDS" '
    (.[0].memories | map(.id)) == $expected[0:10] and .[0].nextCursor != null and
    (.[1].memories | map(.id)) == $expected[10:20] and .[1].nextCursor != null and
    .[2].memories == [] and .[2].nextCursor == null' \
    <<< "$MULTIPLE_PAGE_1"$'\n'"$MULTIPLE_PAGE_2"$'\n'"$MULTIPLE_PAGE_3" > /dev/null

cleanup
trap - EXIT
printf 'Pagination verified: page sizes 10/10/1, exact-multiple 10/10/0, and empty namespace; cleanup verified for %s records.\n' "${#OWNED_IDS[@]}"
