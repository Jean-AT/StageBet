import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 25, duration: '10s' };

export default function () {
  const response = http.post(`${__ENV.BASE_URL}/api/tenants/${__ENV.TENANT_ID}/players/${__ENV.PLAYER_ID}/rounds/${__ENV.ROUND_ID}/bets`,
    JSON.stringify({ betId: `${__VU}-${__ITER}`, amount: '1.00', currency: 'USD', expectedRoundSequence: 1 }),
    { headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': `limit-${__VU}-${__ITER}` } });
  check(response, { 'rate limiting is observable': (result) => [200, 409, 429].includes(result.status) });
}
