import http from 'k6/http';
import { check, sleep } from 'k6';

// Baseline throughput/latency test for POST /api/checkout.
// Run: k6 run baseline-load-test.js
// Override target host: k6 run -e BASE_URL=http://localhost:8080 baseline-load-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID || 1;

export const options = {
  scenarios: {
    ramping_checkout: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },   // ramp up
        { duration: '1m', target: 20 },    // hold
        { duration: '30s', target: 100 },  // spike
        { duration: '1m', target: 100 },   // hold at spike
        { duration: '30s', target: 0 },    // ramp down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],   // 95% of requests under 500ms
    http_req_failed: ['rate<0.05'],     // less than 5% hard failures (network/5xx)
  },
};

export default function () {
  const payload = JSON.stringify({
    productId: Number(PRODUCT_ID),
    customerId: Math.floor(Math.random() * 100000),
    quantity: 1,
  });

  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/checkout`, payload, params);

  check(res, {
    'status is 200 or 409': (r) => r.status === 200 || r.status === 409,
    'no 5xx errors': (r) => r.status < 500,
  });

  sleep(0.2);
}
