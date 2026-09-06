import http from 'k6/http';
import { check } from 'k6';

// Correctness test, not a throughput test: fires a burst of SIMULTANEOUS
// checkout requests for the same product to verify the ConcurrentHashMap
// reservation logic never lets total reserved quantity exceed available stock.
//
// Setup before running:
//   1. Seed a product with a KNOWN, SMALL available_quantity, e.g. 10.
//   2. Set STOCK below to match that seeded quantity.
//   3. Run: k6 run -e PRODUCT_ID=<id> -e STOCK=10 oversell-race-test.js
//
// After running, verify in Postgres:
//   SELECT COUNT(*) FROM orders WHERE product_id = <id> AND status = 'CONFIRMED';
// This count must be <= STOCK. If it's ever greater, the reservation logic
// has an overselling bug.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const STOCK = Number(__ENV.STOCK || 10);

// Fire well more concurrent attempts than there is stock, all at once,
// so the race window is actually exercised.
export const options = {
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: STOCK * 3,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

export default function () {
  const payload = JSON.stringify({
    productId: PRODUCT_ID,
    customerId: __VU, // unique per virtual user
    quantity: 1,
  });

  const params = { headers: { 'Content-Type': 'application/json' } };
  const res = http.post(`${BASE_URL}/api/checkout`, payload, params);

  check(res, {
    'status is 200 (reserved) or 409 (rejected)': (r) => r.status === 200 || r.status === 409,
  });
}
