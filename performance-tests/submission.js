/**
 * K6 performance test for the DISA Returns submission endpoint.
 *
 * POST /monthly/:zReference/:taxYear/:month
 *
 * Each Z-reference requires its own bearer token because the auth layer validates
 * that the token's HMRC-DISA-ORG/ZREF enrolment matches the Z-reference in the URL.
 * Tokens are supplied directly on the command line — no auth-login-stub call is made.
 *
 * ─── Single Z-reference ────────────────────────────────────────────────────────
 *
 *   k6 run \
 *     --env Z_REFERENCE=Z0001 \
 *     --env BEARER_TOKEN="your-bearer-token" \
 *     performance-tests/submission.js
 *
 * ─── Multiple Z-references (VUs round-robin across all pairs) ──────────────────
 *
 *   k6 run \
 *     --env Z_REFERENCES="Z0001,Z0002,Z0003" \
 *     --env BEARER_TOKENS="token-for-Z0001,token-for-Z0002,token-for-Z0003" \
 *     performance-tests/submission.js
 *
 * ─── All environment variables ─────────────────────────────────────────────────
 *
 *   Z_REFERENCE    Single ISA manager Z-reference            (e.g. Z0001)
 *   BEARER_TOKEN   Bearer token for Z_REFERENCE              (e.g. "Bearer eyJ...")
 *                  The "Bearer " prefix is optional — it will be added if missing.
 *
 *   Z_REFERENCES   Comma-separated Z-references              (e.g. "Z0001,Z0002")
 *   BEARER_TOKENS  Comma-separated tokens, same order        (e.g. "token1,token2")
 *                  Must have the same number of entries as Z_REFERENCES.
 *
 *   BASE_URL       Service base URL       (default: http://localhost:1200)
 *   TAX_YEAR       Reporting tax year     (default: 2026-27)
 *   MONTH          Reporting month abbrev (default: JUN)
 *   CLIENT_ID      PPNS client ID         (optional — adds X-Client-ID header)
 *   PAYLOAD_SIZE   small | medium | large | max  (default: medium)
 *                    small  → ~180KB   (100 blocks  × 4 records)
 *                    medium → ~1.8MB   (1000 blocks × 4 records)
 *                    large  → ~5.5MB   (3000 blocks × 4 records)
 *                    max    → ~9MB     (5000 blocks × 4 records, near the 10MB limit)
 *   SCENARIO       smoke | load | stress (default: load)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ─── Config ───────────────────────────────────────────────────────────────────

const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:1200';
const TAX_YEAR    = __ENV.TAX_YEAR    || '2026-27';
const MONTH       = __ENV.MONTH       || 'JUN';
const CLIENT_ID   = __ENV.CLIENT_ID   || '';
const PAYLOAD_SIZE = __ENV.PAYLOAD_SIZE || 'medium';
const SCENARIO    = __ENV.SCENARIO    || 'load';

// ─── Parse Z-reference / token pairs ─────────────────────────────────────────

function parsePairs() {
  // Multi-ref form takes precedence over single-ref form.
  if (__ENV.Z_REFERENCES && __ENV.BEARER_TOKENS) {
    const refs   = __ENV.Z_REFERENCES.split(',').map(s => s.trim()).filter(Boolean);
    const tokens = __ENV.BEARER_TOKENS.split(',').map(s => s.trim()).filter(Boolean);

    if (refs.length !== tokens.length) {
      throw new Error(
        `Z_REFERENCES has ${refs.length} entries but BEARER_TOKENS has ${tokens.length}. They must match.`
      );
    }

    return refs.map((zRef, i) => ({ zRef, token: normaliseToken(tokens[i]) }));
  }

  if (__ENV.Z_REFERENCE && __ENV.BEARER_TOKEN) {
    return [{ zRef: __ENV.Z_REFERENCE.trim(), token: normaliseToken(__ENV.BEARER_TOKEN.trim()) }];
  }

  throw new Error(
    'Provide either:\n' +
    '  --env Z_REFERENCE=Z0001 --env BEARER_TOKEN="<token>"\n' +
    '  --env Z_REFERENCES="Z0001,Z0002" --env BEARER_TOKENS="<token1>,<token2>"'
  );
}

/** Ensure the token starts with "Bearer ". */
function normaliseToken(token) {
  return token.startsWith('Bearer ') ? token : `Bearer ${token}`;
}

// Validate at parse time so the error surfaces immediately rather than inside a VU.
const PAIRS = parsePairs();

// ─── Custom metrics ───────────────────────────────────────────────────────────

const errorCount      = new Counter('submission_errors');
const successRate     = new Rate('submission_success_rate');
const payloadSizeTrend = new Trend('submission_payload_bytes', false);

// ─── Scenarios ────────────────────────────────────────────────────────────────

const scenarios = {
  smoke: {
    executor: 'constant-vus',
    vus: 1,
    duration: '1m',
  },
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m', target: 5 },
      { duration: '5m', target: 5 },
      { duration: '1m', target: 0 },
    ],
  },
  stress: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '2m', target: 5  },
      { duration: '3m', target: 10 },
      { duration: '3m', target: 20 },
      { duration: '2m', target: 30 },
      { duration: '2m', target: 0  },
    ],
  },
};

export const options = {
  scenarios: { submission: scenarios[SCENARIO] || scenarios.load },
  thresholds: {
    http_req_duration:      ['p(95)<10000'],
    submission_success_rate: ['rate>0.95'],
  },
};

// ─── Data generation ──────────────────────────────────────────────────────────

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomNino() {
  return `AA${String(randomInt(0, 999999)).padStart(6, '0')}C`;
}

function randomAccountNumber() {
  return `STD${String(randomInt(1, 999999)).padStart(6, '0')}`;
}

function lisaSubscriptionLine(nino, accountNumber) {
  return JSON.stringify({
    accountNumber, nino,
    firstName: 'PerfTest', middleName: 'LISA Subscription model', lastName: 'User',
    dateOfBirth: '1980-01-22',
    amountTransferredIn: 2001.02, amountTransferredOut: 1988.53,
    dateOfLastSubscription: '2025-06-01',
    totalCurrentYearSubscriptionsToDate: 2500.23,
    marketValueOfAccount: 10000.12,
    isaType: 'LIFETIME',
    dateOfFirstSubscription: '2025-01-22',
    lisaQualifyingAddition: 5000.56, lisaBonusClaim: 3000.56,
  });
}

function lisaClosureLine(nino, accountNumber) {
  return JSON.stringify({
    accountNumber, nino,
    firstName: 'PerfTest', middleName: 'LISA Closure model', lastName: 'User',
    dateOfBirth: '1980-01-22',
    amountTransferredIn: 2500.23, amountTransferredOut: 125.23,
    dateOfLastSubscription: '2025-01-22',
    totalCurrentYearSubscriptionsToDate: 10000.12,
    marketValueOfAccount: 5000.56,
    isaType: 'LIFETIME',
    dateOfFirstSubscription: '2025-06-01',
    closureDate: '2025-03-22', reasonForClosure: 'CLOSED',
    lisaQualifyingAddition: 3000.56, lisaBonusClaim: 4200.54,
  });
}

function sisaSubscriptionLine(nino, accountNumber) {
  return JSON.stringify({
    accountNumber, nino,
    firstName: 'PerfTest', middleName: 'SISA subscription model', lastName: 'User',
    dateOfBirth: '1980-01-22',
    amountTransferredIn: 2500.23, amountTransferredOut: 4560.12,
    dateOfLastSubscription: '2025-06-01',
    totalCurrentYearSubscriptionsToDate: 10000.12,
    marketValueOfAccount: 5678.12,
    isaType: 'STOCKS_AND_SHARES', flexibleIsa: false,
  });
}

function sisaClosureLine(nino, accountNumber) {
  return JSON.stringify({
    accountNumber, nino,
    firstName: 'PerfTest', middleName: 'SISA closure model', lastName: 'User',
    dateOfBirth: '1980-01-22',
    amountTransferredIn: 2500.23, amountTransferredOut: 4560.12,
    dateOfLastSubscription: '2025-06-01',
    totalCurrentYearSubscriptionsToDate: 10000.12,
    marketValueOfAccount: 5678.12,
    isaType: 'INNOVATIVE_FINANCE', flexibleIsa: false,
    closureDate: '2025-07-01', reasonForClosure: 'VOID',
  });
}

/**
 * Build NDJSON. Each block = 4 records (one of each ISA type), ≈ 1.8–2KB.
 *
 *   100  blocks → ~180KB
 *   1000 blocks → ~1.8MB
 *   3000 blocks → ~5.5MB
 *   5000 blocks → ~9MB
 */
function generateNdjson(blockCount) {
  const lines = [];
  for (let i = 0; i < blockCount; i++) {
    lines.push(lisaSubscriptionLine(randomNino(), randomAccountNumber()));
    lines.push(lisaClosureLine(randomNino(), randomAccountNumber()));
    lines.push(sisaSubscriptionLine(randomNino(), randomAccountNumber()));
    lines.push(sisaClosureLine(randomNino(), randomAccountNumber()));
  }
  return lines.join('\n') + '\n';
}

const PAYLOAD_BLOCKS = {
  small:  100,
  medium: 1000,
  large:  3000,
  max:    5000,
};

// ─── Setup (runs once, before all VUs start) ──────────────────────────────────

export function setup() {
  const zRefs = PAIRS.map(p => p.zRef).join(', ');
  console.log(`Z-references: ${zRefs}`);
  console.log(`VUs will round-robin across ${PAIRS.length} Z-reference/token pair(s).`);

  const blocks  = PAYLOAD_BLOCKS[PAYLOAD_SIZE] || PAYLOAD_BLOCKS.medium;
  console.log(`Generating ${PAYLOAD_SIZE} payload (${blocks} blocks)...`);
  const payload = generateNdjson(blocks);
  console.log(`Payload ready: ${(payload.length / 1024 / 1024).toFixed(2)}MB`);

  return { pairs: PAIRS, payload };
}

// ─── Default VU function ──────────────────────────────────────────────────────

export default function ({ pairs, payload }) {
  // Each VU is assigned a fixed Z-reference/token based on its VU number so
  // that concurrent VUs hit different ISA manager accounts.
  const { zRef, token } = pairs[(__VU - 1) % pairs.length];

  const headers = {
    'Content-Type': 'application/x-ndjson',
    Authorization:  token,
  };

  if (CLIENT_ID) {
    headers['X-Client-ID'] = CLIENT_ID;
  }

  const url = `${BASE_URL}/monthly/${zRef}/${TAX_YEAR}/${MONTH}`;
  const res = http.post(url, payload, { headers, timeout: '120s' });

  const ok = check(res, { 'status is 204': (r) => r.status === 204 });

  payloadSizeTrend.add(payload.length);
  successRate.add(ok);

  if (!ok) {
    errorCount.add(1);
    console.error(
      `[VU ${__VU} zRef=${zRef} iter=${__ITER}] FAILED status=${res.status} body=${res.body.substring(0, 300)}`
    );
  }

  sleep(1);
}
