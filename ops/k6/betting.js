import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    betting: { executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '30s', preAllocatedVUs: 5 }
  }
};

export default function () {
  const tenantId = __ENV.TENANT_ID;
  const playerId = __ENV.PLAYER_ID;
  const roundId = __ENV.ROUND_ID;
  const betId = `${__VU}-${__ITER}`;
  const response = http.post(
    `${__ENV.BASE_URL}/api/tenants/${tenantId}/players/${playerId}/rounds/${roundId}/bets`,
    JSON.stringify({ betId, amount: '1.00', currency: 'USD', expectedRoundSequence: 1 }),
    { headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': `k6-${betId}` } }
  );
  check(response, { 'request completed': (r) => r.status < 500 });
}
