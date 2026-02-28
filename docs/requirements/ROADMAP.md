# Unified Notification Platform — Evolution Roadmap

> **Date**: 2026-02-28
> **Version**: v1.0
>
> **Design Principle**: We do not build the completed system from day one.
> Each version must be functional independently and capable of absorbing real-world traffic.
> Before transitioning to the next version, successful load-testing strictly verifying NFR thresholds must be conducted.

---

## Roadmap Overview

```
v1.0                v2.0                v3.0                v4.0
────────────        ────────────        ────────────        ────────────
End-to-End          Multi-Channel       User Control        Infra Stability
Delivery            Reliability         & Scheduling        & Architecture Study

Verify Core         Build 6-Channel     Scheduling &        Advanced Ops +
Pipeline            Reliability         User Control        Architectural Scaling
```

---

## v1.0 — End-to-End Delivery

**GOAL**: Rapidly construct the complete end-to-end flow tracing actual notification delivery.

### Development Scope

```
[Producer]
    │ POST /v1/notifications
    ▼
[Notification API]
    │ Kafka publish
    ▼
[Dispatcher]  ← Channel selection, routing basics
    │
    ├──► [Email Adapter]   → AWS SES / SendGrid
    └──► [In-App Adapter]  → WebSocket (STOMP)
                                   │
                               [Redis]  ← Session Registry
                               [PostgreSQL]  ← Delivery Log
```

### Included Features

| Requirement | Implementation Details |
|---------|---------|
| FR-01 (Channels) | 2 Channels: Email + In-App |
| FR-11 (Idempotency) | Redis-backed duplicate suppression |
| FR-20 (Template) | Hard-coded templates |
| FR-24 (Tracking) | 3 Tiers: `QUEUED / DELIVERED / FAILED` |
| NFR-13 (Auth) | API Key fundamental validations |

### Explicitly Excluded
- FCM / APNs / SMS
- Retry / DLQ
- Rate Limiting
- Scheduled Messages
- User Preferences

### Completion Metrics
- [ ] In-App p95 < 200ms on single node (verified via k6: 100 concurrent users, results documented)
- [ ] Idempotency: duplicate request with same `idempotency-key` returns `200` — no duplicate DB insert confirmed
- [ ] Email: actual SES/SendGrid dispatch confirmed → Delivery Log transitions to `DELIVERED` state
- [ ] Kafka publish failure → Producer retry behavior observed and logged
- [ ] `/actuator/health/liveness` + `/actuator/health/readiness` both return UP

---

## v2.0 — Multi-Channel Reliability

**GOAL**: Enterprise-grade reliability. Operating robustly across multi-channels with 0% message loss.

### Kafka Topic Expansions

```
v1.0: notification.requests (1 Topic)

v2.0:
  notification.requests   ← Ingress gateway
  notification.inapp      ← Dedicated In-App
  notification.push       ← Dedicated FCM/APNs
  notification.sms        ← Dedicated SMS
  notification.email      ← Dedicated Email
  notification.retry      ← Awaiting retries
  notification.dlq        ← Finalized failures
```

### Microservice Topography

```
6 Isolated Processes:
  ① notification-api      (2~5 pods)
  ② dispatcher            (3~10 pods)
  ③ inapp-gateway         (10~50 pods)
  ④ push-worker           (5~20 pods)   ← FCM / APNs
  ⑤ sms-worker            (2~5 pods)
  ⑥ email-worker          (2~5 pods)
```

### Included Features

| Requirement | Implementation Details |
|---------|---------|
| FR-02 (Extensibility) | Define Channel Adapter interface. Rollout FCM/APNs/SMS. |
| FR-03 (Smart Routing) | Redis Presence evaluations routing online/offline destinations. |
| FR-04 (Fallback) | Auto-escalation (FCM Fails → SMS). |
| FR-05 (Priority) | Prioritized Kafka Topics. |
| FR-06 (TTL) | Expired payloads purged during processing. |
| FR-09 (Multi-Device) | Device mapping tables, token expirations. |
| FR-12 (Deduplication) | Redis SET based deduplications (10 min windows). |
| FR-13 (Retry) | Exponential backoffs, max 3 bounds. |
| FR-14 (DLQ) | Failed messages locked in DLQ + Manual Retries API. |
| FR-15 (Rate Limit) | Redis Token Bucket per Recipient/Channel. |
| FR-16 (Circuit Breaker) | Resilience4j isolated channel circuit-tripping. |
| FR-27 (Audit Log) | 90 days retention guarantees. |
| NFR-06 (99.99%) | Kafka `acks=all`, replication factor 3. |
| NFR-08 (Resource Isolate)| Dedicated Consumers limiting channel-specific explosions. |
| NFR-19 (Metrics) | Micrometer + Prometheus + Grafana setup. |
| NFR-20 (Tracing) | OpenTelemetry + Jaeger. |

### Completion Metrics
- [ ] FCM / APNs / SMS actual firing verification
- [ ] One channel's total failure proven to leave others intact
- [ ] Fallbacks observed natively triggering
- [ ] DLQ injections witnessed past 3 active retries
- [ ] Rate limits successfully blocking external traffic via HTTP 429
- [ ] Operational Prometheus dashboards established

---

## v3.0 — User Control & Scheduling

**GOAL**: Enable rich business logics involving user configurations and time-travelled deliveries.

### Schedule Integrations

```
[scheduled_notifications table]
  status: PENDING / PROCESSING / COMPLETED
    │
    │ Quartz polling (10s interval)
    ▼
[Scheduler Service]
  · Detect threshold breaches
  · Single: Explicitly publish directly
  · Bulk: Iterative Kafka publishing based on recipient blocks
```

### Included Features

| Requirement | Implementation Details |
|---------|---------|
| FR-07 (Scheduler) | Scheduler Service + Quartz. Job DB integrations. |
| FR-08 (Quiet Hours) | Holding traffic passing user hours → Auto-drain queues upon daylight. |
| FR-17 (Preference) | Categories & Channel toggling. |
| FR-18 (Opt-out) | Instant GDPR wipeouts & global unsubscriptions. |
| FR-20 (Template Engine) | Embed Handlebars formatting engines. |
| FR-21 (Version Control) | Version tables & template rollbacks. |
| FR-22 (Dynamic Strings) | Binding arguments (`{{name}}`, `{{orderId}}`). |
| FR-23 (i18n) | `MessageSource` i18n bundles processing. |
| FR-25 (Read Receipt) | Internal reading state transmissions. |
| FR-26 (Click Tracking) | Redirection nodes capturing ad conversions. |
| NFR-15 (GDPR) | Immediate opt-out realization. Delete requests handlers. |
| NFR-16 (CAN-SPAM) | Interacting footers onto all HTML emails containing unsubscribes. |
| NFR-17 (Log Masking) | Obfuscations handling phone numbers natively. |

### Completion Metrics
- [ ] Scheduled events ±10 seconds accuracies confirmed
- [ ] Opt-out filtering tested dynamically under production environments
- [ ] Quiet Hours blockades routing perfectly verified
- [ ] International strings rendered accurately mapped via locales
- [ ] Click Tracking mechanisms logging DB interactions correctly

---

## v4.0 — Enterprise Scale

**GOAL**: Constructing big-tech level safeguarding layers and extensive optimization verifications. (Mass campaign handling relegated to Future Scope).

### Mass Campaign Architecture (Future Scope: v5.0)

> **Architectural Note**: Millions of users should not be processed by native RDBMS bounds. Real implementations demand Data Warehouse integrations feeding S3 files dynamically digested by Data Pipelines. These workflows are subjected to design and architecture studying scopes exclusively.

### Load Shedding & Data Tiering

* **API Gateway Backpressure**: Imposing aggressive HTTP 429 statuses natively shutting down traffic ingestion based on deep internal thread/metric overload states.
* **Cold Storage Pipeline Formulation**: Brainstorming partitions effectively porting deep historical data to remote S3 archiving buckets continuously.

### Included Features

| Requirement | Implementation Details |
|---------|---------|
| FR-10 (Campaign) | **[Future Scope]** Data Warehouse based batch architectural designing. |
| FR-28 (Admin API) | Histories, DLQ manual processing, Statistics portals. |
| NFR-01 / 02 | Load-testing verification processes pushing TPS benchmarks N folds. |
| NFR-24 | Dynamic non-destructive Rate Limit parameter modifying systems alongside Gateway Load Shedding protocols. |

### Completion Metrics
- [ ] Pinpointed extreme bottleneck variables logged and systematically resolved (Profiling results documented).
- [ ] Rate limits re-configured securely without dropping instance heartbeats via feature flags.
- [ ] High-volume payloads successfully 429'd avoiding JVM OOM events cleanly.
- [ ] Established architectural drafts mapping S3 Data Tiering flows accurately.

---

## Feature Roadmap Mapping

| Requirement | v1.0 | v2.0 | v3.0 | v4.0 |
|---------|:----:|:----:|:----:|:----:|
| FR-01 Multi-Channel | △ | ✅ | | |
| FR-02 Extensibility | | ✅ | | |
| FR-03 Smart Routing | | ✅ | | |
| FR-04 Fallback | | ✅ | | |
| FR-05 Priority | | ✅ | | |
| FR-06 TTL | | ✅ | | |
| FR-07 Scheduling | | | ✅ | |
| FR-08 Quiet Hours | | | ✅ | |
| FR-09 Multi-Device | | ✅ | | |
| FR-10 Campaign | | | | (Future) |
| FR-11 Idempotency | ✅ | | | |
| FR-12 Deduplication | | ✅ | | |
| FR-13 Retry | | ✅ | | |
| FR-14 DLQ | | ✅ | | |
| FR-15 Rate Limit | | ✅ | | |
| FR-16 Circuit Breaker| | ✅ | | |
| FR-17 Preference | | | ✅ | |
| FR-18 Opt-out | | | ✅ | |
| FR-19 Digest | | | (Future)| |
| FR-20 Template | △ | | ✅ | |
| FR-21 Temp. Version | | | ✅ | |
| FR-22 Variables | | | ✅ | |
| FR-23 i18n | | | ✅ | |
| FR-24 Tracking | ✅ | | | |
| FR-25 Read Receipt | | | ✅ | |
| FR-26 Click Tracking| | | ✅ | |
| FR-27 Audit Log | | ✅ | | |
| FR-28 Admin API | | | | ✅ |
