# Implementation 1: Synchronous HTTP Request-Response

This directory contains the synchronous implementation of the flash sale system.

## Overview

The synchronous approach provides immediate response to purchase requests with direct processing through the following flow:

1. User sends HTTP POST request
2. Request is processed immediately on the same thread
3. Redis atomic operations ensure inventory consistency
4. Database is updated synchronously
5. HTTP response is returned with purchase result

## Key Characteristics

- **Immediate Response**: User gets instant feedback (200 OK or 409 Conflict)
- **Strong Consistency**: ACID guarantees for each transaction
- **Simple Architecture**: Straightforward request-response pattern
- **Blocking Operations**: Each request holds thread until completion

## Performance Expectations

- **Concurrent Users**: Stable up to ~1,000 users
- **Response Time**: 200-500ms typical
- **Throughput**: ~100 requests/second
- **Resource Usage**: Higher CPU/memory due to blocking nature

## When to Use

- Low to medium traffic scenarios
- When immediate user feedback is critical
- Simple business logic without complex workflows
- Development/testing environments

## Architecture Diagram

```
User Request → Controller → Service → Redis DECR → Database → Response
     ↓              ↓         ↓         ↓           ↓         ↓
  HTTP POST    Validation  Business  Atomic Ops  Sync Update  200/409
```

## Configuration

The synchronous implementation uses standard Spring Boot configuration with optimized thread pools for better performance under moderate load.

## Limitations

- Thread pool exhaustion under high concurrency
- Poor user experience during traffic spikes
- Limited scalability compared to async approach
- Higher resource consumption per request
