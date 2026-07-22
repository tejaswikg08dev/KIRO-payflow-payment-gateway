# Hands-On Guide — Phase 16 Part 1: Spring Boot Actuator

## Goal
- Actuator endpoints enabled (/health, /metrics, /info)
- Custom health indicators (DB, Redis, Bank Simulator)
- Understanding of liveness vs readiness probes
- ALB health check integration

---

## What Is Actuator?

```
Spring Boot Actuator exposes operational endpoints:

/actuator/health     → Is the service UP or DOWN?
                       Checks: DB connection, Redis, disk space
                       Used by: ALB health checks (every 30 seconds)

/actuator/metrics    → JVM stats, HTTP request counts, latencies
                       Used by: CloudWatch custom metrics

/actuator/info       → App version, build time, git commit
                       Used by: Deployment verification

ENABLED BY DEFAULT (with actuator dependency):
GET http://localhost:8083/actuator/health
Response: { "status": "UP" }

GET http://localhost:8083/actuator/health/readiness
Response: { "status": "UP", "components": { "db": "UP", "redis": "UP" } }
```

---

## Configuration

```yaml
# In application.yml (each service):
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
        # Expose these endpoints via HTTP
  endpoint:
    health:
      show-details: when-authorized
      # Show component details (db, redis) only to authenticated requests
      # Public /health just shows UP/DOWN
```

---

## ALB Health Check

```
ALB Configuration:
├── Health check path: /actuator/health
├── Interval: 30 seconds
├── Timeout: 5 seconds
├── Healthy threshold: 2 consecutive successes
├── Unhealthy threshold: 3 consecutive failures
└── If unhealthy: ALB stops sending traffic to that instance
```

---

## Custom Health Indicator (Bank Simulator)

```java
@Component
public class BankSimulatorHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Try to connect to bank simulator
        try (Socket socket = new Socket("localhost", 9000)) {
            return Health.up().withDetail("bank", "reachable").build();
        } catch (IOException e) {
            return Health.down().withDetail("bank", "unreachable: " + e.getMessage()).build();
        }
    }
}
```

---

## Phase 16 continues with Parts 2-3:
- Structured logging (JSON format + correlation IDs)
- CloudWatch dashboards and alarms

---

## PROJECT COMPLETE! 🎉

After all 16 phases:
├── 11 microservices running with Swagger docs
├── ISO 8583 bank communication working
├── AI fraud detection scoring every transaction
├── Smart payment routing with failover
├── Reliable webhook delivery with HMAC + retry
├── Daily batch settlement with fee calculation
├── React merchant dashboard + hosted checkout
├── Docker containers for all services
├── CI/CD pipeline with GitHub Actions
├── Deployed on AWS (EC2, RDS, ElastiCache, DynamoDB, SQS, SNS, S3)
└── Monitoring with CloudWatch dashboards and alarms
