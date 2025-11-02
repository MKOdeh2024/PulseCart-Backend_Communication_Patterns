# Load Balancer Configuration

This directory contains load balancer configurations for distributing traffic across multiple instances of the flash sale backend.

## Supported Load Balancers

### 1. nginx (Recommended)
- **File**: `nginx.conf`
- **Algorithm**: Round-robin for sync, least connections for async
- **Features**: Health checks, rate limiting, SSL termination

### 2. HAProxy (Alternative)
- **File**: `haproxy.cfg`
- **Algorithm**: Round-robin for sync, least connections for async
- **Features**: Advanced health checks, rate limiting, SSL, statistics dashboard

## Architecture

```
Internet → Load Balancer → Application Instances
                ↓
        ┌──────────────┐
        │  nginx       │
        │  (Port 80)   │
        └──────────────┘
                ↓
        ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
        │ App Instance │    │ App Instance │    │ App Instance │
        │ (Port 8080)  │    │ (Port 8081)  │    │ (Port 8082)  │
        └──────────────┘    └──────────────┘    └──────────────┘
```

## Configuration Strategy

### Endpoint-based Routing
- **Sync endpoints** (`/api/v1/sync/*`): Round-robin distribution
- **Async endpoints** (`/api/v1/async/*`): Least connections distribution

### Rate Limiting
- **Sync endpoints**: 100 requests/second per IP
- **Async endpoints**: 10 requests/second per IP (higher for flash sales)

### Health Checks
- **Interval**: 5 seconds
- **Failure threshold**: 3 failures
- **Success threshold**: 2 passes

## Deployment

### Single Server Setup
```bash
# Install nginx
sudo apt-get install nginx

# Copy configuration
sudo cp nginx.conf /etc/nginx/sites-available/flashsale

# Enable site
sudo ln -s /etc/nginx/sites-available/flashsale /etc/nginx/sites-enabled/

# Test configuration
sudo nginx -t

# Reload nginx
sudo systemctl reload nginx
```

### Docker Compose Setup
```yaml
version: '3.8'
services:
  # nginx Load Balancer
  nginx-lb:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./load-balancer/nginx.conf:/etc/nginx/conf.d/default.conf
    depends_on:
      - app1
      - app2
      - app3

  # HAProxy Load Balancer (alternative)
  haproxy-lb:
    image: haproxy:alpine
    ports:
      - "81:80"  # Sync frontend
      - "82:81"  # Async frontend
      - "8080:8080"  # Stats page
    volumes:
      - ./load-balancer/haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg
    depends_on:
      - app1
      - app2
      - app3

  app1:
    # Spring Boot app configuration
    ports:
      - "8080"

  app2:
    ports:
      - "8081"

  app3:
    ports:
      - "8082"
```

## Monitoring

### Key Metrics
- **Response time distribution** across instances
- **Error rates** per backend
- **Queue depth** for load balancer
- **Connection counts** per instance

### Health Endpoints
- **nginx load balancer**: `http://localhost/health`
- **nginx status**: `http://localhost:8080/nginx_status`
- **HAProxy stats**: `http://localhost:8080/stats` (admin/admin)
- **Application instances**: `http://localhost:8080/actuator/health`

## Scaling Strategy

### Horizontal Scaling
1. Add more application instances
2. Update load balancer configuration
3. Reload configuration without downtime

### Auto-scaling Triggers
- CPU usage > 70%
- Response time p95 > 2 seconds
- Queue depth > 100 requests

## Security Considerations

### DDoS Protection
- Rate limiting per IP address
- Request size limits
- Connection limits

### SSL/TLS
- SSL termination at load balancer
- Certificate management
- HSTS headers

## Performance Tuning

### nginx Optimizations
```nginx
worker_processes auto;
worker_connections 1024;
use epoll;
tcp_nopush on;
tcp_nodelay on;
```

### Connection Pooling
- Keep-alive connections to backends
- Connection reuse
- Timeout optimization

## Troubleshooting

### Common Issues

1. **502 Bad Gateway**
   - Check if backend instances are healthy
   - Verify ports are accessible
   - Check application logs

2. **504 Gateway Timeout**
   - Increase proxy timeouts
   - Check application performance
   - Monitor backend response times

3. **Rate Limiting Issues**
   - Adjust rate limits based on traffic
   - Use different zones for different endpoints
   - Monitor burst vs sustained traffic
