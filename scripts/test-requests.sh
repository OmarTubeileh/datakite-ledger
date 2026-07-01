#!/usr/bin/env bash
# Manual test requests for the DataKite Async Categorization Ledger.
#
# Prerequisites: the stack is running (docker compose up, or backend/frontend run
# separately) and the backend is reachable at $API_URL.
#
# Usage:
#   ./scripts/test-requests.sh          # run everything
#   Or copy/paste individual curl blocks below directly into your terminal.

API_URL="${API_URL:-http://localhost:8080}"

echo "== 1. Normal transaction — expect INFRASTRUCTURE / CATEGORIZED =="
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": 129.99, "currency": "USD", "date": "2026-07-01T12:00:00Z", "description": "Subscription fee for AWS Cloud us-east-1"}'
echo

echo "== 2. Fraud case (amount > \$5,000) — expect BUSINESS_MEALS / PENDING_REVIEW =="
echo "   (category is still computed; only the status is overridden)"
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": 7500.00, "currency": "USD", "date": "2026-07-01T12:05:00Z", "description": "Team dinner at a restaurant"}'
echo

echo "== 3. Boundary case (amount == \$5,000) — expect OPERATIONS / CATEGORIZED (NOT flagged) =="
echo "   (the rule is 'exceeds \$5,000', so exactly \$5,000 must not be PENDING_REVIEW)"
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000.00, "currency": "USD", "date": "2026-07-01T12:10:00Z", "description": "Office supplies order"}'
echo

echo "== 4. SaaS/Software keywords — expect SAAS_SOFTWARE / CATEGORIZED =="
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": 89.00, "currency": "USD", "date": "2026-07-01T12:15:00Z", "description": "Annual software license renewal for design tool"}'
echo

echo "== 5. No keyword match — expect MISCELLANEOUS / CATEGORIZED (fallback) =="
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": 60.00, "currency": "USD", "date": "2026-07-01T12:20:00Z", "description": "Gift card for employee appreciation"}'
echo

echo "== 6. Invalid payload — expect 400 Bad Request =="
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": -5, "currency": "", "description": ""}'
echo

echo "Waiting 2s for async JMS processing..."
sleep 2

echo "== 7. Ledger feed (GET /api/v1/transactions) =="
curl -s "$API_URL/api/v1/transactions"
echo

echo "== 8. Category analytics (GET /api/v1/transactions/analytics/by-category) =="
curl -s "$API_URL/api/v1/transactions/analytics/by-category"
echo
