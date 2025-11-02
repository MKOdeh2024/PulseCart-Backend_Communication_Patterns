# Implementation 2: Asynchronous Message Queue

This directory contains the asynchronous implementation of the flash sale system.

## Overview

The asynchronous approach provides immediate request acceptance with background processing through message queues:

1. User sends HTTP POST request
2. Request is immediately accepted (202) with tracking ID
3. Purchase request is queued in RabbitMQ
4. Background consumer processes the request
5. Redis atomic operations ensure inventory consistency
6. Database is updated asynchronously

## Key Characteristics

- **Immediate Acceptance**: User gets instant 202 response
- **Decoupled Processing**: Request acceptance separated from processing
- **High Scalability**: Handles thousands of concurrent users
- **Eventual Consistency**: Processing happens in background

## Performance Expectations

- **Concurrent Users**: Scales to 5,000+ users
- **Response Time**: 50-200ms for acceptance
- **Throughput**: 500-1000+ requests/second
- **Resource Usage**: Optimized due to non-blocking nature

## When to Use

- High traffic flash sale scenarios
- When user experience priority over immediate results
- Complex processing workflows
- Production environments with variable load

## Architecture Diagram

```
User Request → Controller → RabbitMQ Queue → Consumer → Redis DECR → Database
     ↓              ↓              ↓            ↓         ↓           ↓
  HTTP POST    202 Accepted    Message      Process  Atomic Ops  Async Update

Background Processing:
Queue Consumer → Inventory Service → Stock Update Events
```

## Configuration

The asynchronous implementation uses:
- RabbitMQ for message queuing
- Multiple consumers for parallel processing
- Dead letter queues for failed messages
- Configurable thread pools for optimal performance

## Advantages

- Superior scalability under high concurrency
- Better resource utilization
- Improved user experience (no waiting)
- Fault isolation (queue failures don't affect acceptance)
- Background processing allows for complex workflows

## Monitoring

Key metrics to monitor:
- Queue depth and processing rates
- Consumer lag and throughput
- Message processing latency
- Dead letter queue growth
- Background processing success rates
