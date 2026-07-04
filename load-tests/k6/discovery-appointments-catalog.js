import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.AUTH_TOKEN || '';
const PROVIDER_ID = __ENV.PROVIDER_ID || '00000000-0000-0000-0000-000000000000';
const OFFERING_ID = __ENV.OFFERING_ID || '00000000-0000-0000-0000-000000000000';
const SLOT_ID = __ENV.SLOT_ID || '00000000-0000-0000-0000-000000000000';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || '00000000-0000-0000-0000-000000000000';
const PET_ID = __ENV.PET_ID || '00000000-0000-0000-0000-000000000000';
const DELIVERY_ADDRESS_ID =
  __ENV.DELIVERY_ADDRESS_ID || '00000000-0000-0000-0000-000000000000';

export const options = {
  scenarios: {
    discovery_appointments_catalog_1000_vus: {
      executor: 'ramping-vus',
      stages: [
        { duration: '2m', target: 250 },
        { duration: '3m', target: 1000 },
        { duration: '5m', target: 1000 },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<750'],
  },
};

function headers(role = 'CUSTOMER') {
  const base = {
    'Content-Type': 'application/json',
    'X-User-Id': CUSTOMER_ID,
    'X-User-Role': role,
  };
  if (TOKEN) {
    base.Authorization = `Bearer ${TOKEN}`;
  }
  return base;
}

export default function () {
  const discovery = http.get(
    `${BASE_URL}/api/v1/discovery/providers?latitude=17.385&longitude=78.4867&radiusKm=10`,
    { headers: headers() }
  );
  check(discovery, {
    'discovery status is 2xx/4xx': (r) => r.status >= 200 && r.status < 500,
  });

  const offerings = http.get(
    `${BASE_URL}/api/v1/catalog/offerings?providerId=${PROVIDER_ID}`,
    { headers: headers() }
  );
  check(offerings, {
    'offerings status is 2xx/4xx': (r) => r.status >= 200 && r.status < 500,
  });

  const slots = http.get(
    `${BASE_URL}/api/v1/catalog/slots?offeringId=${OFFERING_ID}`,
    { headers: headers() }
  );
  check(slots, {
    'slots status is 2xx/4xx': (r) => r.status >= 200 && r.status < 500,
  });

  const holdPayload = JSON.stringify({
    customerId: CUSTOMER_ID,
    providerId: PROVIDER_ID,
    offeringId: OFFERING_ID,
    slotId: SLOT_ID,
    petId: PET_ID,
    priceAmount: 500.0,
    payAtClinic: false,
  });
  const hold = http.post(`${BASE_URL}/api/v1/appointments/hold`, holdPayload, {
    headers: headers(),
  });
  check(hold, {
    'appointment hold status is expected': (r) =>
      [200, 201, 400, 409, 429].includes(r.status),
  });

  const orderPayload = JSON.stringify({
    customerId: CUSTOMER_ID,
    providerId: PROVIDER_ID,
    deliveryAddressId: DELIVERY_ADDRESS_ID,
    items: [{ offeringId: OFFERING_ID, quantity: 1 }],
    deliveryFee: 50.0,
    discountAmount: 0.0,
  });
  const order = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
    headers: headers(),
  });
  check(order, {
    'order create status is expected': (r) =>
      [200, 201, 400, 404, 409, 422, 429].includes(r.status),
  });

  let orderId = null;
  if (order.status === 201) {
    try {
      const body = order.json();
      orderId = body.orderId || body.id;
    } catch (_) {
      orderId = null;
    }
  }

  if (orderId) {
    const dispatch = http.get(
      `${BASE_URL}/api/v1/dispatch/jobs/by-order/${orderId}`,
      { headers: headers() }
    );
    check(dispatch, {
      'dispatch job lookup status is expected': (r) =>
        [200, 404].includes(r.status),
    });

    const paymentPayload = JSON.stringify({
      userId: CUSTOMER_ID,
      referenceId: orderId,
      amount: 550.0,
      transactionType: 'ORDER',
    });
    const payment = http.post(
      `${BASE_URL}/api/v1/payments/orders`,
      paymentPayload,
      { headers: headers() }
    );
    check(payment, {
      'payment order status is expected': (r) =>
        [200, 201, 400, 403, 404, 409, 422, 429, 502, 503].includes(r.status),
    });
  }

  sleep(1);
}
