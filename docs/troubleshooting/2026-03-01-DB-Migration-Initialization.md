# Troubleshooting: Database Migration Initialization (2026-03-01)

## Issue: Missing Database Tables (`missing table [delivery_logs]`)

### Symptoms
The application failed to start with a `SchemaManagementException`, stating that tables were missing, even though Flyway was enabled and migration scripts were present in `db/migration`.

### Root Cause
**Initialization Order Conflict**: In Spring Boot 3.3.4, JPA validation (`ddl-auto: validate`) was triggered before the Flyway migration could run. Since the database was empty, validation failed and terminated the process before Flyway had a chance to create the tables.

### Final Resolution
**Manual Out-of-Band Migration via Gradle**:
- Added `org.flywaydb.flyway` plugin and PostgreSQL driver to the `buildscript` block in `build.gradle`.
- Executed migration manually via Gradle: `./gradlew flywayMigrate`.
- This ensured tables existed before the Spring application context attempted JPA validation.
- Future application startups will now pass validation as the schema is already established.
