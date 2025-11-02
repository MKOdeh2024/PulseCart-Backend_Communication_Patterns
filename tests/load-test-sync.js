import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const purchaseSuccessRate = new Rate('purchase_success_rate');
const purchaseResponseTime = new Trend('purchase_response_time');

// Test configuration
export const options = {
  scenarios: {
    // Ramp up test - simulates flash sale traffic
    ramp_up_flash_sale: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },   // Warm up
        { duration: '1m', target: 500 },    // Moderate load
        { duration: '2m', target: 1000 },   // Peak flash sale load
        { duration: '1m', target: 1000 },   // Sustained peak
        { duration: '30s', target: 0 },     // Cool down
      ],
      tags: { test_type: 'sync_flash_sale' },
    },

    // Constant load test - simulates normal traffic
    constant_load: {
      executor: 'constant-vus',
      vus: 50,
      duration: '5m',
      startTime: '6m', // Start after ramp-up test
      tags: { test_type: 'sync_constant' },
    },
  },

  thresholds: {
    // Response time thresholds
    http_req_duration: ['p(50)<500', 'p(95)<2000', 'p(99)<5000'],

    // Error rate threshold
    http_req_failed: ['rate<0.1'],

    // Custom metrics
    purchase_success_rate: ['rate>0.95'], // 95% success rate
    purchase_response_time: ['p(95)<3000'], // 95% of purchases under 3s
  },
};

// Base URL - will be overridden by environment variable
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Test data
const PRODUCT_ID = 1;
const QUANTITY = 1;

// Setup function - runs before the test starts
export function setup() {
  console.log('Starting synchronous purchase load test');
  console.log(`Target URL: ${BASE_URL}`);
  console.log(`Test Product ID: ${PRODUCT_ID}, Quantity: ${QUANTITY}`);

  // Initialize stock for testing
  const initResponse = http.post(`${BASE_URL}/api/v1/sync/stock/init/${PRODUCT_ID}`);
  if (initResponse.status !== 200) {
    console.error(`Failed to initialize stock: ${initResponse.status} ${initResponse.body}`);
  }

  // Reset stock to 1000 for testing
  const resetResponse = http.put(`${BASE_URL}/api/v1/sync/stock/${PRODUCT_ID}/reset?stock=1000`);
  if (resetResponse.status !== 200) {
    console.error(`Failed to reset stock: ${resetResponse.status} ${resetResponse.body}`);
  }

  console.log('Setup complete - stock initialized to 1000');
}

// Main test function
export default function () {
  const startTime = new Date().getTime();

  // Generate unique user ID for this VU
  const userId = `user_${__VU}_${__ITER}`;

  // Purchase request payload
  const payload = JSON.stringify({
    productId: PRODUCT_ID,
    quantity: QUANTITY,
    userId: userId
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '10s', // 10 second timeout
  };

  // Make purchase request
  const response = http.post(`${BASE_URL}/api/v1/sync/purchase`, payload, params);

  const endTime = new Date().getTime();
  const responseTime = endTime - startTime;

  // Record custom metrics
  purchaseResponseTime.add(responseTime);

  // Check response
  const isSuccess = check(response, {
    'status is 200 or 409': (r) => r.status === 200 || r.status === 409,
    'response time < 5000ms': (r) => r.timings.duration < 5000,
    'has valid response structure': (r) => {
      try {
        const jsonResponse = JSON.parse(r.body);
        return jsonResponse.hasOwnProperty('success') &&
               jsonResponse.hasOwnProperty('message');
      } catch (e) {
        return false;
      }
    },
    'successful purchase has orderId': (r) => {
      if (r.status === 200) {
        try {
          const jsonResponse = JSON.parse(r.body);
          return jsonResponse.success && jsonResponse.orderId;
        } catch (e) {
          return false;
        }
      }
      return true; // Not applicable for 409 responses
    },
  });

  // Record success rate
  purchaseSuccessRate.add(isSuccess);

  // Log failures for debugging
  if (!isSuccess) {
    console.error(`Purchase failed - VU: ${__VU}, Iter: ${__ITER}, Status: ${response.status}, Time: ${responseTime}ms, Body: ${response.body}`);
  }

  // Random sleep between 100ms - 1s to simulate realistic user behavior
  sleep(Math.random() * 0.9 + 0.1);
}

// Teardown function - runs after the test completes
export function teardown(data) {
  console.log('Load test completed');

  // Check final stock level
  const stockResponse = http.get(`${BASE_URL}/api/v1/sync/stock/${PRODUCT_ID}`);
  if (stockResponse.status === 200) {
    try {
      const stockData = JSON.parse(stockResponse.body);
      console.log(`Final stock level: ${stockData.stock}`);
    } catch (e) {
      console.error('Failed to parse final stock response');
    }
  } else {
    console.error(`Failed to get final stock: ${stockResponse.status}`);
  }
}
