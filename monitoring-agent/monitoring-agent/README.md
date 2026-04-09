# Intelligent System Monitoring Agent

An AI-powered Spring Boot monitoring agent that detects CPU, memory, and disk
anomalies using rule-based logic, compares with historical baselines, and
enriches alerts using Claude (or Ollama) LLM before dispatching to Slack,
PagerDuty, or any generic webhook.

---

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Redis (local or Docker)
- (Optional) Claude API key or local Ollama instance

### 1. Start Redis
```bash
docker run -d -p 6379:6379 redis:7-alpine
```

### 2. Clone and configure
```bash
# Copy and edit the config
cp src/main/resources/application.yml application-local.yml
# Edit CLAUDE_API_KEY, SLACK_WEBHOOK_URL, device IDs etc.
```

### 3. Run
```bash
# With demo mode (no Site24x7 key needed — synthetic data generated)
mvn spring-boot:run

# With real credentials via env vars
CLAUDE_API_KEY=sk-ant-xxx \
SLACK_WEBHOOK_URL=https://hooks.slack.com/xxx \
SLACK_ENABLED=true \
mvn spring-boot:run
```

The agent starts polling every **60 seconds** by default.
In demo mode it injects a CPU spike every 5th cycle so you can see alerts fire immediately.

---

## Environment Variables

| Variable             | Required | Default      | Description                              |
|----------------------|----------|--------------|------------------------------------------|
| `CLAUDE_API_KEY`     | No*      | —            | Anthropic API key (*required for LLM)   |
| `SITE247_API_KEY`    | No       | demo-key     | Site24x7 API key (empty = demo mode)    |
| `REDIS_HOST`         | No       | localhost    | Redis hostname                           |
| `REDIS_PORT`         | No       | 6379         | Redis port                               |
| `REDIS_PASSWORD`     | No       | —            | Redis password (if ACL enabled)          |
| `LLM_ENABLED`        | No       | true         | Enable/disable LLM enrichment           |
| `SLACK_ENABLED`      | No       | false        | Enable Slack alerts                      |
| `SLACK_WEBHOOK_URL`  | No*      | —            | Slack incoming webhook URL               |
| `WEBHOOK_ENABLED`    | No       | false        | Enable generic webhook alerts            |
| `ALERT_WEBHOOK_URL`  | No*      | —            | Target webhook URL                       |
| `PAGERDUTY_ENABLED`  | No       | false        | Enable PagerDuty incidents               |
| `PAGERDUTY_KEY`      | No*      | —            | PagerDuty Events API integration key     |

---

## Architecture

```
[Site24x7 / Demo] → MetricsFetcherService
                         ↓
                   AnomalyDetectorService   ← MetricsHistoryService (Redis)
                         ↓
                   LlmExplainerService      ← Claude API / Ollama
                         ↓
                   AlertDispatcherService   → LogAlertChannel      (always on)
                                           → SlackAlertChannel     (opt-in)
                                           → WebhookAlertChannel   (opt-in)
                                           → PagerDutyAlertChannel (opt-in)
                                           → AlertRecordRepository (DB audit)
```

### Anomaly Rules

| Rule             | Logic                                             | Severity         |
|------------------|---------------------------------------------------|------------------|
| Absolute CPU     | cpu ≥ 95%                                         | CRITICAL         |
| Absolute CPU     | cpu ≥ 80%                                         | HIGH             |
| CPU Spike        | current − baseline ≥ 30% (configurable)           | MEDIUM → CRITICAL|
| Sustained CPU    | last 5 readings all ≥ 80%                         | HIGH             |
| Absolute Memory  | mem ≥ 95%                                         | CRITICAL         |
| Absolute Memory  | mem ≥ 85%                                         | HIGH             |
| Absolute Disk    | disk ≥ 98%                                        | CRITICAL         |
| Absolute Disk    | disk ≥ 90%                                        | HIGH             |

### False Positive Prevention
- Rolling baseline (last N readings in Redis) instead of static thresholds
- Per-host per-metric cooldown window (15 min default)
- Spike detection only activates after ≥ 3 historical readings
- Maintenance window API to silence alerts during deployments

---

## REST API

| Method | Path                          | Description                          |
|--------|-------------------------------|--------------------------------------|
| GET    | /api/alerts                   | All alert history                    |
| GET    | /api/alerts?hostId=X          | Alerts for a specific host           |
| GET    | /api/alerts?severity=CRITICAL | Filter by severity                   |
| GET    | /api/alerts/stats             | Counts by severity level             |
| POST   | /api/maintenance/{hostId}?minutes=60 | Set maintenance window         |
| DELETE | /api/maintenance/{hostId}     | Clear maintenance window             |
| GET    | /api/status                   | Agent health check                   |

### Example: Set maintenance window
```bash
curl -X POST "http://localhost:8080/api/maintenance/demo-host-01?minutes=30"
```

### Example: Query recent CRITICAL alerts
```bash
curl "http://localhost:8080/api/alerts?severity=CRITICAL"
```

---

## Adding a New Alert Channel

1. Create a class in `com.ossom.monitoring.channel`
2. Implement `AlertChannel`
3. Annotate with `@Component` (and optionally `@ConditionalOnProperty`)

```java
@Component
@ConditionalOnProperty(name = "agent.teams.enabled", havingValue = "true")
public class TeamsAlertChannel implements AlertChannel {
    @Override public boolean supports(Severity s) { return s != Severity.LOW; }
    @Override public void send(AlertPayload p) { /* POST to Teams webhook */ }
}
```

Spring auto-discovers it — no other wiring needed.

---

## Adding a New Metrics Source

Replace or supplement `MetricsFetcherService.fetchAll()`. The method must return
`List<MetricSnapshot>`. Everything downstream is source-agnostic.

---

## Switching to Ollama (local LLM)

```yaml
agent:
  llm:
    enabled: true
    provider: ollama
    model: llama3          # or mistral, phi3, etc.
    ollama-base-url: http://localhost:11434
```

Pull the model first: `ollama pull llama3`

---

## Production Checklist

- [ ] Replace H2 with PostgreSQL: uncomment driver in `pom.xml`, update `datasource` in `application.yml`
- [ ] Set `spring.jpa.hibernate.ddl-auto=validate` (not `create-drop`)
- [ ] Configure Redis with password / TLS
- [ ] Mount API keys as Kubernetes Secrets, not env vars in Deployment YAML
- [ ] Set `agent.poll-interval-ms` to your desired scrape interval
- [ ] Enable Prometheus scraping: `GET /actuator/prometheus`
- [ ] Set up Grafana dashboard on `monitoring.alerts.fired` and `monitoring.pipeline.duration`
- [ ] Tune cooldown and spike delta thresholds based on your baseline variance

---

## Running Tests

```bash
mvn test
```

Tests are unit-level with Mockito. No Redis or external services required.
