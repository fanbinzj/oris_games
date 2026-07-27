#!/usr/bin/env bash
# Deploys the leaderboard worker to Cloudflare via raw API calls.
# Usage: CLOUDFLARE_API_TOKEN=xxx ./backend/deploy.sh
# Token needs: Account > Workers Scripts > Edit, Account > Workers KV Storage > Edit.
set -euo pipefail

API="https://api.cloudflare.com/client/v4"
AUTH=(-H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN:?set CLOUDFLARE_API_TOKEN}")
DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT_NAME="dino-leaderboard"
KV_TITLE="dino-scores"

jqget() { python3 -c "import json,sys;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1"; }

echo "== verify token"
curl -sf "${AUTH[@]}" "$API/user/tokens/verify" | jqget "d['result']['status']"

echo "== account id"
ACCOUNT_ID=$(curl -sf "${AUTH[@]}" "$API/accounts" | jqget "d['result'][0]['id']")
echo "account: $ACCOUNT_ID"

echo "== KV namespace (reuse or create)"
NS_ID=$(curl -sf "${AUTH[@]}" "$API/accounts/$ACCOUNT_ID/storage/kv/namespaces?per_page=100" \
  | jqget "next((n['id'] for n in d['result'] if n['title']=='$KV_TITLE'),'')")
if [ -z "$NS_ID" ]; then
  NS_ID=$(curl -sf -X POST "${AUTH[@]}" -H "Content-Type: application/json" \
    -d "{\"title\":\"$KV_TITLE\"}" "$API/accounts/$ACCOUNT_ID/storage/kv/namespaces" \
    | jqget "d['result']['id']")
fi
echo "kv namespace: $NS_ID"

echo "== upload worker"
METADATA=$(cat <<JSON
{"main_module":"worker.js",
 "compatibility_date":"2026-07-01",
 "bindings":[{"type":"kv_namespace","name":"SCORES","namespace_id":"$NS_ID"}]}
JSON
)
curl -sf -X PUT "${AUTH[@]}" \
  -F "metadata=$METADATA;type=application/json" \
  -F "worker.js=@$DIR/leaderboard-worker.js;type=application/javascript+module" \
  "$API/accounts/$ACCOUNT_ID/workers/scripts/$SCRIPT_NAME" | jqget "d['success']"

echo "== workers.dev subdomain"
SUBDOMAIN=$(curl -s "${AUTH[@]}" "$API/accounts/$ACCOUNT_ID/workers/subdomain" \
  | jqget "(d.get('result') or {}).get('subdomain','')")
if [ -z "$SUBDOMAIN" ]; then
  # No subdomain registered yet: derive one from the account id and register it.
  WANT="dino-$(echo "$ACCOUNT_ID" | cut -c1-10)"
  SUBDOMAIN=$(curl -sf -X PUT "${AUTH[@]}" -H "Content-Type: application/json" \
    -d "{\"subdomain\":\"$WANT\"}" "$API/accounts/$ACCOUNT_ID/workers/subdomain" \
    | jqget "d['result']['subdomain']")
fi
echo "subdomain: $SUBDOMAIN"

echo "== enable workers.dev route for the script"
curl -sf -X POST "${AUTH[@]}" -H "Content-Type: application/json" \
  -d '{"enabled":true,"previews_enabled":false}' \
  "$API/accounts/$ACCOUNT_ID/workers/scripts/$SCRIPT_NAME/subdomain" >/dev/null || true

URL="https://$SCRIPT_NAME.$SUBDOMAIN.workers.dev"
echo "== smoke test: $URL"
for i in $(seq 1 24); do
  if curl -sf --max-time 8 "$URL/top" >/dev/null 2>&1; then break; fi
  sleep 5
done
curl -sf -X POST --data "Setup Test|1" "$URL/submit"
echo
echo "WORKER_URL=$URL"
