import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    burst_test: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 300,
      maxVUs: 2000,
      stages: [
        { target: 500, duration: '20s' },
        { target: 1000, duration: '40s' },
        { target: 1000, duration: '60s' },
        { target: 200, duration: '20s' }
      ]
    }
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    checks: ['rate>0.95']
  }
};

export function setup() {
  const res = http.post(`${BASE_URL}/auth/token?userId=load-user`);
  check(res, { 'token request succeeded': (r) => r.status === 200 });

  const data = res.json();
  return { token: data.token };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`
  };

  const target = Math.random() < 0.6
    ? `${BASE_URL}/api/service-a/ping?delayMs=2`
    : `${BASE_URL}/api/service-b/ping?delayMs=2`;

  const res = http.get(target, { headers });

  check(res, {
    'accepted or rejected': (r) => r.status === 200 || r.status === 429,
    'rate limit headers included': (r) => !!r.headers['X-RateLimit-Algorithm']
  });
}
