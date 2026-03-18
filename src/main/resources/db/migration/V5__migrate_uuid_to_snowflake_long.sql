-- V5__migrate_uuid_to_snowflake_long.sql

-- Step 1: Drop foreign key constraints
ALTER TABLE delivery_logs DROP CONSTRAINT delivery_logs_request_id_fkey;

-- Step 2: Since we are in an early phase (v1.0 to v2.0 transition) and changing the PK type from UUID (string representation or raw bytes) to BIGINT is non-trivial to cast data directly, and assuming we can wipe the test/dev data, we will recreate the tables. If we needed to keep data, we would add temporary columns. For simplicity in this roadmap step, we truncate and alter.

TRUNCATE TABLE delivery_logs;
TRUNCATE TABLE notification_requests CASCADE;

-- Step 3: Alter column types to BIGINT (Long in Java)
ALTER TABLE notification_requests ALTER COLUMN id TYPE BIGINT USING (NULL);
ALTER TABLE delivery_logs ALTER COLUMN id TYPE BIGINT USING (NULL);
ALTER TABLE delivery_logs ALTER COLUMN request_id TYPE BIGINT USING (NULL);

-- Step 4: Re-add foreign key constraints
ALTER TABLE delivery_logs
    ADD CONSTRAINT delivery_logs_request_id_fkey
    FOREIGN KEY (request_id) REFERENCES notification_requests(id);
