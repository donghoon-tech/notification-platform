# Troubleshooting: Kafka KRaft Connectivity (2026-03-01)

## Issue: Kafka Connectivity Failure (`Bootstrap broker disconnected`)

### Symptoms
Kafka consumers/producers failed to connect to the broker with "Connection to node -1 could not be established" and "Node may not be available" errors.

### Root Cause
**KRaft Listener Configuration Mismatch**: The transition from Zookeeper to KRaft mode requires precise listener configuration to allow communication both inside the Docker network and from the host (localhost). The broker was advertising an internal container address to the application running on the host machine, which the host could not resolve.

### Final Resolution
**Separated Internal and External Listeners**:
- Updated `docker-compose.yml` to define two distinct listeners: `INTERNAL` and `EXTERNAL`.
- `KAFKA_LISTENERS`: `INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:29093`
- `KAFKA_ADVERTISED_LISTENERS`: `INTERNAL://notification-kafka:29092,EXTERNAL://127.0.0.1:9092`
- This ensures that clients connecting via `localhost:9092` are instructed to continue using `127.0.0.1:9092` for subsequent requests, resolving the disconnection issue.
- Used a fixed `CLUSTER_ID` to maintain cluster stability across restarts.
