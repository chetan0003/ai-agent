# Order Pipeline Anomaly Detection Agent

AI agent built with Spring Boot 3.x + Claude (Anthropic API) that monitors
order pipeline metrics across regions and raises alerts when anomalies are detected.

---

## Tech stack

| Layer        | Technology                          |
|--------------|-------------------------------------|
| Framework    | Spring Boot 3.2.x, Java 17, Maven   |
| LLM          | Claude claude-sonnet-4-20250514 (Anthropic API) |
| Database     | PostgreSQL (order data + audit log) |
| Cache        | Redis (short-term agent memory)     |
| Alerting     | Slack webhook                       |
| Observability| Micrometer + Prometheus             |

---

## Package structure

```
com.yourorg.agent
├── AgentApplication.java
├── agent/
│   ├── OrderPipelineAgent.java      # @Scheduled agent loop
│   └── AgentDecision.java           # LLM response model
├── prompt/
│   └── PromptBuilder.java           # Prompt assembly
├── client/
│   └── AnthropicClient.java         # Claude API wrapper
├── tool/
│   ├── AgentTool.java               # Tool interface
│   ├── OrderMetricsTool.java        # DB metrics fetcher
│   └── SlackAlertTool.java          # Slack dispatcher
├── memory/
│   └── AgentMemoryService.java      # Redis + PostgreSQL memory
├── model/
│   ├── OrderMetrics.java
│   ├── AgentDecisionEntity.java
│   └── enums/
│       ├── AnomalyType.java
│       └── Severity.java
├── repository/
│   ├── OrderRepository.java
│   └── AgentDecisionRepository.java
├── config/
│   ├── AgentProperties.java
│   ├── WebClientConfig.java
│   └── RedisConfig.java
└── controller/
    └── AgentController.java
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL running on `localhost:5432`
- Redis running on `localhost:6379`
- Anthropic API key
- Slack webhook URL

---

## Configuration

Copy and set these environment variables before running:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

---

## Build & run

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/order-pipeline-agent-1.0.0-SNAPSHOT.jar
```

Or via Maven:

```bash
mvn spring-boot:run
```

---

## REST API endpoints

| Method | Endpoint                          | Description                          |
|--------|-----------------------------------|--------------------------------------|
| POST   | `/api/agent/trigger`              | Trigger full cycle (all regions)     |
| POST   | `/api/agent/trigger/{region}`     | Trigger cycle for one region         |
| GET    | `/api/agent/decisions`            | All stored anomaly decisions         |
| GET    | `/api/agent/decisions/{region}`   | Decisions for a specific region      |
| GET    | `/api/agent/decisions/anomalies`  | Only decisions where anomaly=true    |
| GET    | `/api/agent/health`               | Agent health probe                   |

### Trigger example

```bash
curl -X POST http://localhost:8080/api/agent/trigger/SRI_LANKA
```

---

## Extending the agent

### Add a new tool (e.g. PagerDuty alert)

1. Create `PagerDutyAlertTool implements AgentTool` in `tool/`
2. Annotate with `@Component("send_pagerduty_alert")`
3. The LLM can now return `"send_pagerduty_alert"` in `toolsToCall` and Spring
   will route to your new tool automatically — no changes to the agent loop.

### Add a new region

Add the region string to `agent.anomaly.regions` in `application.yml`.

### Tune anomaly thresholds

Adjust `agent.anomaly.order-drop-threshold-pct` and
`agent.anomaly.failure-rate-threshold-pct` in `application.yml`.

---

## Running tests

```bash
mvn test
```

Tests mock the Anthropic client and Redis — no live infrastructure required
to run the test suite.
