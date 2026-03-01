# ADR-01: DB-Kafka Consistency Strategy

## Context
When a notification request is received via the Ingress API, we must ensure that it is both persisted in the database for tracking and published to Kafka for asynchronous processing. Since PostgreSQL and Apache Kafka are separate distributed systems, ensuring atomic operations across them is challenging.

We evaluated three primary strategies:
1. **Transactional Outbox Pattern**: Store messages in a DB table within the same transaction and use a separate relay to publish to Kafka.
2. **Post-Commit Event**: Publish to Kafka immediately after the DB transaction successfully commits.
3. **Dual Write**: Write to both systems independently.

## Decision
We have decided to adopt **Strategy 2: Post-Commit Event**.

### Rationale
- **Performance (Low Latency)**: Unlike the Outbox pattern, there is no polling delay or CDC overhead. This is critical for meeting our p99 < 100ms latency target.
- **Simplicity**: Easier to implement and maintain using Spring's transaction synchronization or `@TransactionalEventListener`.
- **Reliability**: While there is a small risk of message loss if the process crashes after commit but before Kafka publishing, this can be mitigated with a "Reaper Batch" (Self-healing mechanism) that periodically finds `PENDING` requests and re-publishes them.

## Consequences
- **At-least-once Delivery**: Because we might re-publish failed events via a Reaper Batch, the downstream consumers (Dispatcher) **must be idempotent**.
- **Self-healing requirement**: A background job (Reaper) must be implemented to handle edge-case failures where a commit succeeds but the event is not published.
