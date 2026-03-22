# Smart Routing & Fallback Design Spec

> **STATUS: DRAFT** 
> This document was generated at the end of the previous session. The user has **NOT YET APPROVED** this specific design. Do not proceed to implementation or plan writing until explicit approval is given.

## 1. Overview
This document outlines the architectural design for the "Smart Routing and Fallback" feature (FR-03, FR-04) planned for v2.0 of the Notification Platform. The goal is to intelligently route notifications based on user presence (online/offline status) and automatically fall back to alternative channels when the primary channel is unreachable.

## 2. Core Architecture Strategy: "Interface Abstraction"
To ensure the system can start simple and scale to a massive global architecture (like Slack/Discord) without rewriting core logic, we will decouple "Presence Management" from "Notification Routing".

### 2.1 Presence Component (`PresenceManager`)
A dedicated interface will abstract how user presence is determined.
- **Interface**: `PresenceManager` with a method `boolean isOnline(String userId)`.
- **v2.0 Implementation (`WebSocketRedisPresenceManager`)**:
  - Listens to Spring WebSocket `SessionConnectedEvent` and `SessionDisconnectEvent`.
  - Updates a Redis key (`presence:{userId}`) when a user connects/disconnects.
  - The `isOnline()` method simply checks for the existence of this Redis key.
- **Future Scalability (v4.0+)**: When traffic necessitates a dedicated external Presence Server, we only need to write a new `ExternalApiPresenceManager` implementation. The core routing logic remains untouched.

### 2.2 Routing Component (Dispatcher Enhancement)
The `DispatcherService` will use the `PresenceManager` to make intelligent routing decisions before publishing to Kafka.
- **Rule Engine**:
  - If target channel is `IN_APP`:
    - Call `presenceManager.isOnline(userId)`.
    - If `True` -> Publish to `notification.inapp` Kafka topic.
    - If `False` -> Do NOT publish to In-App. Trigger the **Fallback Logic**.

### 2.3 Fallback Component
When a message cannot be delivered via the requested channel (e.g., user is offline for an In-App message), the system will automatically reroute it.
- **v2.0 Initial Fallback Chain**: `IN_APP` (Offline) -> `EMAIL`.
- **Mechanism**:
  - If Fallback is triggered, a new `DeliveryLog` entity is created for the fallback channel (`EMAIL`) with status `PENDING`.
  - The message is then published to the `notification.email` Kafka topic instead of the originally requested topic.
  - *Note: As new channels like Push (FCM) are added, this chain will evolve to `IN_APP` -> `PUSH` -> `EMAIL`.*

## 3. Data Flow Diagram
```text
[Request: IN_APP Notification] 
       │
       ▼
[DispatcherService] ──(queries)──> [PresenceManager]
       │                                  │ (Checks Redis)
       ├─(True: Online)                   └──> Returns boolean
       │
       └──> [Kafka: notification.inapp] (Proceed normal delivery)
       
       │
       └─(False: Offline) 
            │
            └──> [Fallback Triggered] Change target channel to EMAIL
                 │
                 └──> Create new DeliveryLog for EMAIL
                 │
                 └──> [Kafka: notification.email] (Rerouted delivery)
```

## 4. Next Steps for Next Session
1. Read this document to restore context.
2. Formally commit this document to the ADR tracking folder if required.
3. Use the `writing-plans` skill to break down the implementation of `PresenceManager` and `DispatcherService` updates into bite-sized tasks.
