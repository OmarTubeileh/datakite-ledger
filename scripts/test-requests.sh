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

echo "== 6. Groq check — Infrastructure implied WITHOUT any rule-based keyword =="
echo "   (none of RuleBasedCategorizationService's keywords appear in this description —"
echo "   'DigitalOcean'/'droplets' isn't in its keyword list. If Groq is actually being"
echo "   used, expect INFRASTRUCTURE. If it comes back MISCELLANEOUS instead, the LLM call"
echo "   is failing/falling back — check the backend log for a 'falling back to rule-based"
echo "   matching' WARN line to confirm.)"
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": 75.00, "currency": "USD", "date": "2026-07-01T12:25:00Z", "description": "Monthly bill from DigitalOcean for compute instances"}'
echo

echo "== 7. Groq check — Business Meals implied WITHOUT any rule-based keyword =="
echo "   (same idea: 'Olive Garden'/'outing' isn't in the keyword list either. Expect"
echo "   BUSINESS_MEALS if Groq is working, MISCELLANEOUS if it's silently falling back.)"
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": 64.50, "currency": "USD", "date": "2026-07-01T12:30:00Z", "description": "Team outing at Olive Garden after the sprint demo"}'
echo

echo "== 8. Invalid payload — expect 400 Bad Request =="
curl -i -X POST "$API_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d '{"amount": -5, "currency": "", "description": ""}'
echo

echo "Waiting 2s for async JMS processing..."
sleep 2

echo "== 9. Ledger feed (GET /api/v1/transactions) =="
curl -s "$API_URL/api/v1/transactions"
echo

echo "== 10. Category analytics (GET /api/v1/transactions/analytics/by-category) =="
curl -s "$API_URL/api/v1/transactions/analytics/by-category"
echo
