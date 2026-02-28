# Unified Notification Platform — Requirements

> **Date**: 2026-02-28
> **Version**: v1.0
> **Status**: Finalized

---

## 1. Purpose & Scope

Design a Unified Notification Platform that centrally manages various channels (In-App, Mobile Push, SMS, Email).

**Core Values**:
- Extensibility: Add new channels with minimal changes to existing systems.
- Reliability: Zero message loss.
- Performance: Support millions of concurrent users.
- Operability: Full traceability of delivery histories.

---

## 2. Stakeholders

| Role | Concerns |
|------|--------|
| **Producer (Internal Service)** | Request notifications via a simple API |
| **End User** | Receive messages timely, without duplicates, on preferred channels |
| **Operations Team** | Delivery history, failure detection, retries |
| **Business Team** | Large-scale marketing campaigns, A/B testing |

---

## 3. Functional Requirements (FR)

### 3.1 Channel Support

| ID | Requirement | Description |
|----|---------|------|
| FR-01 | Multi-Channel Support | In-App (WebSocket/SSE), Mobile Push (FCM/APNs), SMS, Email |
| FR-02 | Channel Extensibility | Add new channels purely by implementing adapters without changing existing core code |

### 3.2 Routing & Delivery Control

| ID | Requirement | Description |
|----|---------|------|
| FR-03 | Smart Routing | Auto-detect user online presence (Online → In-App, Offline → FCM) |
| FR-04 | Fallback Chain | Auto-escalate to the next priority channel upon failure. Priority configurable. |
| FR-05 | Priority Levels | 4 Levels: `CRITICAL` / `HIGH` / `NORMAL` / `MARKETING`. Process highest first. |
| FR-06 | TTL (Time-To-Live) | Message expiration time. Auto-skip delivery for expired messages. |
| FR-07 | Scheduled Delivery | Deliver at specific times. Supports both single and large-scale targets. |
| FR-08 | Quiet Hours | Hold messages during user-configured quiet hours → Auto-deliver when quiet hours end. |
| FR-09 | Multi-Device | 1 User to N devices support. Device token management and expiration handling. |
| FR-10 | Bulk / Campaign | **[Future Scope]** Mass delivery campaigns (Data Warehouse pipeline architecture study). |

### 3.3 Reliability

| ID | Requirement | Description |
|----|---------|------|
| FR-11 | Idempotency | Reject duplicate requests associated with the same `idempotency-key`. |
| FR-12 | Deduplication | Ensure recipients receive exactly 1 message even if multiple identical paths trigger it. |
| FR-13 | Retry + Backoff | Exponential backoff logic upon failure (Default: 3 max retries, up to 5 mins delay). |
| FR-14 | Dead Letter Queue (DLQ) | Store final failed messages in DLQ. Admin manual retry supported. |
| FR-15 | Rate Limiting | Throttling per recipient (Spam control) + per sender (API Quota) + per channel (SMS Costs). |
| FR-16 | Circuit Breaker | Auto-block and auto-recover from external channel API outages. |

### 3.4 User Control

| ID | Requirement | Description |
|----|---------|------|
| FR-17 | Notification Preference | Users can toggle notification reception by channel/category. |
| FR-18 | Opt-in / Opt-out | Changes to reception consent reflected immediately and persisted (GDPR Compliant). |
| FR-19 | Digest / Batching | **[Future Scope]** Group multi-notifications in a short window into 1 digest message (Stateful Stream Processing study). |

### 3.5 Template & Content

| ID | Requirement | Description |
|----|---------|------|
| FR-20 | Template Engine | Channel-specific templates (Email=HTML, SMS=160 chars, Push=title/body). |
| FR-21 | Template Versioning | Version control for templates with rollback functionality. |
| FR-22 | Dynamic Variable Binding | Injection of context variables like `{{name}}`, `{{orderId}}`. |
| FR-23 | i18n / Localization | Auto-translation of messages based on user locale/language. |

### 3.6 Tracking & Analytics

| ID | Requirement | Description |
|----|---------|------|
| FR-24 | Delivery Tracking | State machine: `PENDING → QUEUED → DISPATCHED → DELIVERED → FAILED`. |
| FR-25 | Read Receipt | Track whether users have read In-App notifications. |
| FR-26 | Click Tracking | Track notification clicks to measure marketing CTR. |
| FR-27 | Audit Log | Persist requester, channel, timestamp, and content history for 90 days. |

### 3.7 Operations

| ID | Requirement | Description |
|----|---------|------|
| FR-28 | Admin API | Endpoint for fetching delivery history, DLQ reprocessing, template management, and stats. |

---

## 4. Non-Functional Requirements (NFR)

### 4.1 Performance

| ID | Requirement | Target |
|----|---------|--------|
| NFR-01 | Connection/Throughput Profiling | Conduct load testing to identify bottlenecks and document multi-fold optimization in TPS/Connections. |
| NFR-02 | Load Shedding | Auto-block requests at API Gateway level (429 Backpressure) to prevent worker crashes under peak loads. |
| NFR-03 | In-App Delivery Latency | p99 < **100ms** |
| NFR-04 | Push / SMS Delivery Latency | p99 < **5 seconds** |
| NFR-05 | Scheduled Delivery Accuracy | Within **±10 seconds** of target time |

### 4.2 Reliability

| ID | Requirement | Target |
|----|---------|--------|
| NFR-06 | Service Availability | **99.99%** (< 52 minutes downtime/year) |
| NFR-07 | Message Loss Rate | < **0.001%** |
| NFR-08 | Channel Fault Isolation | FCM downtime strictly isolated from Email/SMS. |
| NFR-09 | Graceful Shutdown | Zero loss of in-flight messages upon instance termination. |
| NFR-10 | Graceful Degradation | Core pipeline survives even if secondary infra (e.g., Redis Cache for deduplication) fails. |

### 4.3 Scalability

| ID | Requirement | Target |
|----|---------|--------|
| NFR-11 | Stateless Gateway | Connection capacity scales linearly with Pod additions. |
| NFR-12 | Consumer Scalability | Throughput scales linearly with Kafka partition expansion. |
| NFR-13 | Channel Expansion | New channels added silently via implementing 1 adapter without modifying existing logic. |

### 4.4 Security & Compliance

| ID | Requirement | Description |
|----|---------|------|
| NFR-14 | Authentication | External = API Key, Internal Service-to-Service = JWT. |
| NFR-15 | GDPR | Immediate opt-out realization, data retention compliance, right-to-erasure support. |
| NFR-16 | CAN-SPAM | Marketing emails must auto-include embedded unsubscribe links. |
| NFR-17 | Log Masking | Phone numbers and emails strictly obscured in plain-text logs. |
| NFR-18 | Sensitive Channel Encryption | Forced TLS for internal services, E2E encryption optionally provided per channel. |

### 4.5 Operability

| ID | Requirement | Description |
|----|---------|------|
| NFR-19 | Metrics | Prometheus aggregation. Dashboards for volume / failure rate / latency / consumer lag. |
| NFR-20 | Distributed Tracing | OpenTelemetry → Jaeger. Context propagation via `messageId`. |
| NFR-21 | Structured Logging | JSON formatted logs. PI data filtered. |
| NFR-22 | Log Retention (Data Tiering) | DB Partitioning strategies + Cold Storage archiving architecture design for 90-day retention policies. |
| NFR-23 | Zero-Downtime Deployment | Rolling deployments supported fundamentally. |
| NFR-24 | Dynamic Configuration | Adjust Circuit Breaker limitations & Rate limits via Feature Flags without server restarts. |

---

## 5. System Constraints

- Language: **Java 21** (Virtual Threads utilization)
- Framework: **Spring Boot 3.3+**
- Other Stacks: Flexible based on requirement optimizations.

---

## 6. External Dependencies

| Service | Purpose | Alternative |
|--------|------|------|
| FCM (Firebase) | Android Push | - |
| APNs (Apple) | iOS Push | - |
| Twilio / AWS SNS | SMS | Local Providers |
| AWS SES / SendGrid | Email | Direct SMTP |

---

## 7. Glossary

| Term | Definition |
|------|------|
| **Producer** | Standard internal/external services requesting notification deliveries. |
| **Dispatcher** | Core service responsible for channel routing, template rendering, and fallback executions. |
| **Channel Adapter** | Component abstracting external APIs (FCM, SMS, Email). |
| **Delivery Log** | Persisted history summarizing delivery attempts and outcomes. |
| **DLQ** | Dead Letter Queue. Repository storing permanently failed payloads. |
| **Idempotency Key** | Client-provided unique strings suppressing repetitive processing. |
| **Fan-out** | Process characterizing scattering 1 internal event to N user targets. |
| **Quiet Hours** | Silent hours predefined by consumers restricting push alerts. |
| **Digest** | Tying massive alerts originating in a confined timeframe into 1 digest block. |
