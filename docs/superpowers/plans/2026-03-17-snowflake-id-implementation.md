# Snowflake ID Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace UUID primary keys with a custom Snowflake ID generator to solve B-Tree index fragmentation and ensure exact chronological ordering.

**Architecture:** A thread-safe `SnowflakeIdGenerator` utility will be implemented using bitwise operations. It will rely on a custom epoch (2026-01-01) and an injected `MACHINE_ID` from the environment. We will migrate existing `UUID` columns to `BIGINT` via Flyway. Entities and DTOs will be updated from `UUID` to `Long`. 

**Tech Stack:** Java 21, Spring Boot 3.3.4, PostgreSQL, Flyway, JUnit 5.

---

## Chunk 1: Generator Implementation

### Task 1: Create `SnowflakeIdGenerator`

**Files:**
- Create: `src/main/java/com/notification/platform/config/SnowflakeIdGenerator.java`
- Create: `src/test/java/com/notification/platform/config/SnowflakeIdGeneratorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.notification.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("Should generate unique IDs sequentially")
    void shouldGenerateUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        assertThat(id1).isLessThan(id2);
        assertThat(id1).isPositive();
    }
    
    @Test
    @DisplayName("Should handle concurrent ID generation without duplicates")
    void shouldHandleConcurrency() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        int threadCount = 100;
        int idsPerThread = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Long> generatedIds = new HashSet<>();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < idsPerThread; j++) {
                    synchronized (generatedIds) {
                        generatedIds.add(generator.nextId());
                    }
                }
                latch.countDown();
            });
        }
        latch.await();
        assertThat(generatedIds).hasSize(threadCount * idsPerThread);
    }
    
    @Test
    @DisplayName("Should throw exception if machine ID is out of bounds")
    void shouldValidateMachineId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1024L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*SnowflakeIdGeneratorTest*"`
Expected: FAIL (Compilation error or test failure)

- [ ] **Step 3: Write minimal implementation**

```java
package com.notification.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SnowflakeIdGenerator {

    // Custom Epoch (2026-01-01T00:00:00Z)
    private static final long CUSTOM_EPOCH = 1767225600000L;

    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    private final long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(@Value("${notification.snowflake.machine-id:1}") long machineId) {
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException(String.format("Machine Id can't be greater than %d or less than 0", MAX_MACHINE_ID));
        }
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long currentTimestamp = timestampGen();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - currentTimestamp));
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timestampGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timestampGen();
        }
        return timestamp;
    }

    protected long timestampGen() {
        return System.currentTimeMillis();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*SnowflakeIdGeneratorTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/notification/platform/config/SnowflakeIdGenerator.java src/test/java/com/notification/platform/config/SnowflakeIdGeneratorTest.java
git commit -m "feat: implement SnowflakeIdGenerator"
```

---

## Chunk 2: Database Migration

### Task 2: Create Flyway Migration Script

**Files:**
- Create: `src/main/resources/db/migration/V5__migrate_uuid_to_snowflake_long.sql`

- [ ] **Step 1: Create the SQL script**

```sql
-- V5__migrate_uuid_to_snowflake_long.sql

-- Step 1: Drop foreign key constraints
ALTER TABLE delivery_logs DROP CONSTRAINT delivery_logs_request_id_fkey;

-- Step 2: Since we are in an early phase (v1.0 to v2.0 transition) and changing the PK type from UUID (string representation or raw bytes) to BIGINT is non-trivial to cast data directly, and assuming we can wipe the test/dev data, we will recreate the tables. If we needed to keep data, we would add temporary columns. For simplicity in this roadmap step, we truncate and alter.

TRUNCATE TABLE delivery_logs;
TRUNCATE TABLE notification_requests;

-- Step 3: Alter column types to BIGINT (Long in Java)
ALTER TABLE notification_requests ALTER COLUMN id TYPE BIGINT USING (NULL);
ALTER TABLE delivery_logs ALTER COLUMN id TYPE BIGINT USING (NULL);
ALTER TABLE delivery_logs ALTER COLUMN request_id TYPE BIGINT USING (NULL);

-- Step 4: Re-add foreign key constraints
ALTER TABLE delivery_logs
    ADD CONSTRAINT delivery_logs_request_id_fkey
    FOREIGN KEY (request_id) REFERENCES notification_requests(id);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V5__migrate_uuid_to_snowflake_long.sql
git commit -m "db: create V5 migration to change UUID to BIGINT"
```

---

## Chunk 3: Entity and Logic Refactoring

### Task 3: Update Entities, DTOs, and Events

**Files:**
- Modify: `src/main/java/com/notification/platform/domain/entity/NotificationRequest.java`
- Modify: `src/main/java/com/notification/platform/domain/entity/DeliveryLog.java`
- Modify: `src/main/java/com/notification/platform/api/dto/response/NotificationSendResponse.java`
- Modify: `src/main/java/com/notification/platform/messaging/event/NotificationRequestCreatedEvent.java`
- Modify: `src/main/java/com/notification/platform/messaging/event/NotificationRequestEvent.java`
- Modify: `src/main/java/com/notification/platform/domain/repository/NotificationRequestRepository.java`
- Modify: `src/main/java/com/notification/platform/domain/repository/DeliveryLogRepository.java`

- [ ] **Step 1: Replace `UUID` with `Long` in all specified files**
  - Search for `UUID id` and `UUID requestId` and change to `Long id` and `Long requestId`.
  - Update generic types in Repositories from `<NotificationRequest, UUID>` to `<NotificationRequest, Long>`.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/notification/platform/domain/entity/ src/main/java/com/notification/platform/api/dto/response/ src/main/java/com/notification/platform/messaging/event/ src/main/java/com/notification/platform/domain/repository/
git commit -m "refactor: change ID types from UUID to Long in entities and dtos"
```

### Task 4: Inject Generator and Replace `UUID.randomUUID()`

**Files:**
- Modify: `src/main/java/com/notification/platform/service/NotificationService.java`
- Modify: `src/main/java/com/notification/platform/dispatcher/DispatcherService.java`

- [ ] **Step 1: Update `NotificationService.java`**
  - Inject `SnowflakeIdGenerator`.
  - Replace `UUID.randomUUID()` with `snowflakeIdGenerator.nextId()`.
  - Fix any `UUID.fromString()` parsing if caching previously used strings (Longs can be parsed with `Long.parseLong()`).

- [ ] **Step 2: Update `DispatcherService.java`**
  - Inject `SnowflakeIdGenerator`.
  - Replace `UUID.randomUUID()` for `DeliveryLog` ID generation with `snowflakeIdGenerator.nextId()`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/notification/platform/service/NotificationService.java src/main/java/com/notification/platform/dispatcher/DispatcherService.java
git commit -m "feat: integrate SnowflakeIdGenerator into services"
```

### Task 5: Fix Compilation and Tests

**Files:**
- Modify: `src/test/java/com/notification/platform/service/NotificationServiceTest.java`
- Modify: `src/test/java/com/notification/platform/dispatcher/DispatcherServiceTest.java`
- (And any other tests that fail compilation due to UUID -> Long changes)

- [ ] **Step 1: Update Tests**
  - Change mock setups from `UUID.randomUUID()` to a dummy Long (e.g., `12345L`).
  - Ensure all assertions match the new `Long` types.

- [ ] **Step 2: Run all tests to verify**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/test/java/
git commit -m "test: update tests to accommodate Long IDs"
```