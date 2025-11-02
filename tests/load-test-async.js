import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const asyncAcceptanceRate = new Rate('async_acceptance_rate');
const asyncResponseTime = new Trend('async_response_time');

// Test configuration
export const options = {
  scenarios: {
    // Ramp up test - simulates flash sale traffic for async
    ramp_up_async_flash_sale: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 200 },   // Warm up (higher for async)
        { duration: '1m', target: 1000 },   // Moderate load
        { duration: '2m', target: 2000 },   // Peak flash sale load
        { duration: '1m', target: 2000 },   // Sustained peak
        { duration: '30s', target: 0 },     // Cool down
      ],
      tags: { test_type: 'async_flash_sale' },
    },

    // Burst test - simulates sudden traffic spikes
    burst_traffic: {
      executor: 'constant-vus',
      vus: 100,
      duration: '2m',
      startTime: '6m', // Start after ramp-up test
      tags: { test_type: 'async_burst' },
    },
  },

  thresholds: {
    // Async responses should be very fast (just acceptance)
    http_req_duration: ['p(50)<200', 'p(95)<500', 'p(99)<1000'],

    // Very low error rate for acceptance
    http_req_failed: ['rate<0.05'],

    // Custom metrics
    async_acceptance_rate: ['rate>0.99'], // 99% acceptance rate
    async_response_time: ['p(95)<300'], // 95% under 300ms
  },
};

// Base URL - will be overridden by environment variable
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Test data
const PRODUCT_ID = 1;
const QUANTITY = 1;

// Setup function - runs before the test starts
export function setup() {
  console.log('Starting asynchronous purchase load test');
  console.log(`Target URL: ${BASE_URL}`);
  console.log(`Test Product ID: ${PRODUCT_ID}, Quantity: ${QUANTITY}`);

  // Initialize stock for testing
  const initResponse = http.post(`${BASE_URL}/api/v1/sync/stock/init/${PRODUCT_ID}`);
  if (initResponse.status !== 200) {
    console.error(`Failed to initialize stock: ${initResponse.status} ${initResponse.body}`);
  }

  // Reset stock to 2000 for testing (higher for async load)
  const resetResponse = http.put(`${BASE_URL}/api/v1/sync/stock/${PRODUCT_ID}/reset?stock=2000`);
  if (resetResponse.status !== 200) {
    console.error(`Failed to reset stock: ${resetResponse.status} ${resetResponse.body}`);
  }

  console.log('Setup complete - stock initialized to 2000');
}

// Main test function
export default function () {
  const startTime = new Date().getTime();

  // Generate unique user ID for this VU
  const userId = `async_user_${__VU}_${__ITER}`;

  // Async purchase request payload
  const payload = JSON.stringify({
    productId: PRODUCT_ID,
    quantity: QUANTITY,
    userId: userId
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '5s', // Shorter timeout for async (just acceptance)
  };

  // Make async purchase request
  const response = http.post(`${BASE_URL}/api/v1/async/purchase`, payload, params);

  const endTime = new Date().getTime();
  const responseTime = endTime - startTime;

  // Record custom metrics
  asyncResponseTime.add(responseTime);

  // Check response - async should return 202 Accepted immediately
  const isAccepted = check(response, {
    'status is 202 Accepted': (r) => r.status === 202,
    'response time < 1000ms': (r) => r.timings.duration < 1000,
    'has valid async response structure': (r) => {
      try {
        const jsonResponse = JSON.parse(r.body);
        return jsonResponse.hasOwnProperty('accepted') &&
               jsonResponse.hasOwnProperty('trackingId') &&
               jsonResponse.hasOwnProperty('message');
      } catch (e) {
        return false;
      }
    },
    'accepted request has trackingId': (r) => {
      if (r.status === 202) {
        try {
          const jsonResponse = JSON.parse(r.body);
          return jsonResponse.accepted && jsonResponse.trackingId;
        } catch (e) {
          return false;
        }
      }
      return false;
    },
  });

  // Record acceptance rate
  asyncAcceptanceRate.add(isAccepted);

  // Log failures for debugging
  if (!isAccepted) {
    console.error(`Async purchase failed - VU: ${__VU}, Iter: ${__ITER}, Status: ${response.status}, Time: ${responseTime}ms, Body: ${response.body}`);
  }

  // Shorter random sleep for async (50ms - 200ms) - faster request rate
  sleep(Math.random() * 0.15 + 0.05);
}

// Teardown function - runs after the test completes
export function teardown(data) {
  console.log('Async load test completed');

  // Check final stock level after async processing
  const stockResponse = http.get(`${BASE_URL}/api/v1/sync/stock/${PRODUCT_ID}`);
  if (stockResponse.status === 200) {
    try {
      const stockData = JSON.parse(stockResponse.body);
      console.log(`Final stock level after async processing: ${stockData.stock}`);
    } catch (e) {
      console.error('Failed to parse final stock response');
    }
  } else {
    console.error(`Failed to get final stock: ${stockResponse.status}`);
  }

  // Give some time for async processing to complete
  console.log('Waiting 30 seconds for async processing to complete...');
  sleep(30);

  // Final stock check
  const finalStockResponse = http.get(`${BASE_URL}/api/v1/sync/stock/${PRODUCT_ID}`);
  if (finalStockResponse.status === 200) {
    try {
      const finalStockData = JSON.parse(finalStockResponse.body);
      console.log(`Final stock level after waiting: ${finalStockData.stock}`);
    } catch (e) {
      console.error('Failed to parse final stock response');
    }
  }
}
