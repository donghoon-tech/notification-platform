# ADR-03: Snowflake ID and Client-Side Reordering

## Context
As the Notification Platform scales towards v2.0 to handle massive, multi-channel traffic, relying on standard `UUID.randomUUID()` for primary keys in database entities (`NotificationRequest`, `DeliveryLog`) presents critical bottlenecks. Random UUIDs cause severe B-Tree index fragmentation in PostgreSQL, degrading write performance over time. Additionally, random UUIDs do not preserve the chronological order of creation, making it impossible to determine the exact sequence of events generated within the same millisecond across distributed nodes. To solve this, we evaluated distributed ID generation strategies including TSID (random-based) and traditional Snowflake (sequence-based).

## Decision
We have decided to adopt a **Custom Snowflake ID Generator** for all notification platform entities, coupled with a **Client-Side Reordering** strategy (200ms buffer window) on the frontend.

The Snowflake generator will produce a 64-bit Long consisting of:
- **41 bits:** Timestamp (Custom epoch: `2026-01-01T00:00:00Z`)
- **10 bits:** Machine ID (Injected via environment variables)
- **12 bits:** Sequence (Sequential auto-incrementing per millisecond)

### Rationale
- **Database Performance**: 64-bit Longs are highly efficient for B-Tree indexing compared to 128-bit UUIDs, preventing page splits and keeping inserts appended to the end of the tree.
- **Exact Chronological Causality**: Unlike TSID (which uses randomness for the trailing bits and can invert causality within the same millisecond), the traditional Snowflake approach uses an incrementing sequence. This guarantees that within a single machine, IDs are strictly monotonically increasing, preserving exact millisecond-level causal ordering.
- **No Central Bottleneck**: IDs are generated purely in local memory without relying on external sequences (like PostgreSQL Auto Increment) or network hops (like Redis).
- **Client-Side Reordering**: By using a distributed ID that is globally time-sortable, we avoid the heavy architectural burden of forcing messages for a specific user to route through a single sticky node. Instead, any node can publish a message, and the client (e.g., mobile app) buffers incoming messages for 200ms, sorting them by their Snowflake ID to reconstruct the exact correct order before displaying them.

## Consequences
- **Infrastructure Requirement**: Each deployed instance of the application must be assigned a unique `Machine ID` (between 0 and 1023) via environment variables to guarantee global uniqueness.
- **Clock Drift Vulnerability**: If the system clock of an instance moves backward (e.g., NTP adjustment), the generator must halt (throw an exception) until the clock catches up, to prevent duplicate or temporally inverted IDs.
- **Schema Migration**: Existing database tables using `UUID` must be migrated to `BIGINT` (Long), requiring Flyway schema alterations.
