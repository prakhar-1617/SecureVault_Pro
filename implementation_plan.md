# SecureVault Pro — Implementation Plan

A production-style Java 17 desktop application that securely encrypts, stores, and manages files and passwords. Designed to demonstrate maximum interview depth across OOP, DSA, concurrency, security, and design patterns.

---

## User Review Required

> [!IMPORTANT]
> **Before execution begins, please confirm the following:**
> 1. **MySQL** — Do you have MySQL installed locally? If not, should we use **SQLite** (zero-setup, file-based) instead?
> 2. **JavaFX** — Should the UI be a full JavaFX GUI, or is a **rich CLI** (with ANSI colors, menus, tables) acceptable as a first iteration?
> 3. **Maven vs Gradle** — The plan uses Maven as stated in the review. Is that your preference, or do you have Gradle set up?
> 4. **Java version** — Confirm Java 17 is installed (`java -version`). The project will use Java 17 language features.

---

## Open Questions

> [!NOTE]
> These do not block the plan but will affect specific implementation choices:
> - Should the Brute Force Simulator (Module 11) be included? It is educational-only but some institutions flag it.
> - Should JUnit 5 tests be written alongside each module, or in a final pass?

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| GUI | JavaFX 21 |
| Database | MySQL 8 (or SQLite fallback) |
| Security | PBKDF2WithHmacSHA256, AES-256-GCM, SHA-256 |
| Concurrency | ExecutorService + PriorityBlockingQueue |
| Patterns | Strategy, Factory, Decorator, Singleton, Observer |
| Streams | BufferedInputStream + NIO FileChannel |
| Cache | LinkedHashMap (LRU policy) |
| Build | Maven 3.9 |
| Testing | JUnit 5 + Mockito |

---

## Proposed Changes

### Phase 1 — Foundation

#### [NEW] `pom.xml` (project root)
Maven project descriptor. Dependencies: JavaFX 21, MySQL Connector/J 8, JUnit 5, Mockito. Java 17 compiler target. JavaFX Maven plugin for `mvn javafx:run`.

#### [NEW] `src/main/java/com/securevault/config/ConfigurationManager.java`
- **Singleton** (thread-safe, double-checked locking)
- Reads `config.properties` once on startup
- Keys: `thread.pool.size`, `cache.size`, `default.algorithm`, `buffer.size`, `db.url`, `db.user`
- Provides typed getters: `getInt()`, `getString()`, `getBoolean()`

#### [NEW] `src/main/resources/config.properties`
Default configuration file shipped with the project.

#### [NEW] Database Schema — `src/main/resources/schema.sql`
Tables: `users`, `files`, `credentials`, `audit_logs`, `analytics`

```sql
-- users: failedAttempts, accountLocked, lastLogin, createdAt
-- files: fileId, ownerId, originalName, encryptedPath, checksum, algorithm, size, uploadTime
-- credentials: id, ownerId, website, username, encryptedPassword, notes, lastModified
-- audit_logs: id, userId, action, detail, timestamp
-- analytics: id, algorithm, operationType, durationMs, fileSize, threadId, timestamp
```

#### [NEW] `src/main/java/com/securevault/database/DatabaseManager.java`
- **Singleton** wrapping JDBC `Connection`
- Connection pooling via `LinkedList<Connection>` (simple pool, 5 connections)
- `getConnection()`, `releaseConnection()`, `executeQuery()`, `executeUpdate()`
- Runs `schema.sql` on first launch (auto-creates tables)

#### [NEW] Custom Exception Framework
```
exceptions/
  SecureVaultException.java       (base)
  AuthenticationException.java
  EncryptionException.java
  FileStorageException.java
  IntegrityException.java
  ConfigurationException.java
```

#### [NEW] `src/main/java/com/securevault/util/HashUtil.java`
SHA-256 utility — `hash(byte[])`, `hashFile(Path)`, `verify(byte[], String)`

---

### Phase 2 — Security & Authentication

#### [NEW] `auth/User.java`
Fields: `userId`, `username`, `salt`, `hashedPassword`, `failedAttempts`, `accountLocked`, `lastLogin`, `createdAt`

#### [NEW] `auth/PasswordHasher.java`
- PBKDF2WithHmacSHA256, 310 000 iterations, 256-bit key
- `generateSalt()`, `hash(password, salt)`, `verify(password, salt, hash)`

#### [NEW] `auth/AuthenticationService.java`
- `register(username, password)` — validates strength, hashes, stores in DB
- `login(username, password)` — checks lock, verifies hash, increments `failedAttempts`, locks after 5 failures
- `unlock(username)` — admin reset
- Fires `AuthEvent` (Observer)

#### [NEW] Encryption Strategy Pattern
```
encryption/
  EncryptionStrategy.java         (interface: encrypt/decrypt byte[])
  AESStrategy.java                (AES-256-GCM, random IV per call)
  XORStrategy.java                (XOR with repeating key)
  CaesarStrategy.java             (byte-level Caesar cipher)
  EncryptionFactory.java          (createStrategy(String name) — OCP)
```

#### [NEW] `encryption/CustomPipelineStrategy.java`
Chains multiple strategies sequentially; serializes chain info for decryption.

---

### Phase 3 — Storage Pipeline

#### [NEW] Decorator Pipeline
```
decorators/
  EncryptionDecorator.java        (abstract decorator wrapping EncryptionStrategy)
  ReverseDecorator.java           (reverses byte array)
  ShiftDecorator.java             (bit-shift transformation)
  XORDecorator.java               (XOR layer)
  MappingDecorator.java           (byte substitution table)
  PipelineBuilder.java            (reads pipeline.json, builds chain programmatically)
```

#### [NEW] `src/main/resources/pipeline.json`
Default pipeline config: `["AES", "Reverse", "XOR"]`

#### [NEW] `storage/FileStorageService.java`
- `uploadFile(Path source, int userId, String algorithm)` — streams via `BufferedInputStream`, encrypts, writes to `encrypted/` directory, stores metadata in DB
- `downloadFile(int fileId, Path dest)` — decrypts, verifies checksum, writes
- NIO `FileChannel` + `ByteBuffer` for large files (> 10 MB threshold from config)
- Fires `FileEvent` (Observer)

#### [NEW] `integrity/ChecksumManager.java`
- Stores `{checksum, algorithm, timestamp}` per file
- `compute(Path)`, `verify(Path, stored)`, `getHistory(fileId)`

---

### Phase 4 — Concurrency

#### [NEW] `task/TaskPriority.java` (enum: HIGH, NORMAL, LOW)

#### [NEW] `task/FileTask.java`
- Implements `Comparable<FileTask>` for `PriorityBlockingQueue`
- Fields: `taskId`, `filePath`, `userId`, `operation`, `priority`, `submittedAt`

#### [NEW] `thread/ThreadPoolManager.java`
- `ExecutorService` backed by `PriorityBlockingQueue<FileTask>`
- Pool size from `ConfigurationManager`
- `submit(FileTask)`, `shutdown()`, `getActiveCount()`, `getQueueSize()`
- Worker threads fire `TaskEvent` on completion (Observer)

#### [NEW] `logger/AuditLogger.java`
- **Singleton** with `ConcurrentLinkedQueue<LogEntry>` (thread-safe)
- Async flush to DB every N seconds via `ScheduledExecutorService`
- `log(userId, action, detail)`, `getLogs(userId)`, `exportCSV(Path)`

---

### Phase 5 — Performance

#### [NEW] `cache/LRUCache.java`
- Generic `LRUCache<K, V>` backed by `LinkedHashMap` with `accessOrder=true`
- Keyed by **file content hash** (not filename) — survives renames
- Capacity from `ConfigurationManager`
- Thread-safe via `ReentrantReadWriteLock`
- Tracks hit/miss ratio

#### [NEW] `analytics/AnalyticsService.java`
- Tracks: avg encryption time, avg decryption time, fastest/slowest algorithm, thread utilization, cache hit ratio, total throughput, failures
- `PriorityQueue<AlgorithmStat>` (comparator: slowest-first for bottleneck report)
- Implements `Observer` (auto-updated via events — no manual `analytics.record()` calls)
- `generateReport()` returns structured `AnalyticsReport`

#### [NEW] Observer / Event System
```
events/
  EventBus.java                   (Singleton pub/sub dispatcher)
  SecureVaultEvent.java           (base event)
  AuthEvent.java
  FileEvent.java
  TaskEvent.java
  EncryptionEvent.java
  EventListener.java              (functional interface)
```

---

### Phase 6 — User Features

#### [NEW] `vault/Credential.java`
Fields: `id`, `ownerId`, `website`, `username`, `encryptedPassword`, `notes`, `lastModified`

#### [NEW] `vault/PasswordVaultService.java`
- CRUD operations on credentials (password encrypted with user's AES key)
- `search(keyword)` using linear scan + `TreeMap` for sorted display

#### [NEW] JavaFX UI
```
ui/
  MainApp.java                    (Application entry point)
  LoginController.java
  RegisterController.java
  DashboardController.java
  FileManagerController.java
  VaultController.java
  AnalyticsDashboardController.java
  SettingsController.java
```
FXML files in `src/main/resources/fxml/`. CSS theme in `src/main/resources/styles/dark-theme.css`.

**Design**: Dark glassmorphism theme, sidebar navigation, animated file upload progress, real-time analytics charts (JavaFX Charts API).

---

### Phase 7 — Quality

#### [NEW] JUnit 5 Tests
```
src/test/java/com/securevault/
  auth/PasswordHasherTest.java
  auth/AuthenticationServiceTest.java
  encryption/AESStrategyTest.java
  encryption/EncryptionFactoryTest.java
  cache/LRUCacheTest.java
  integrity/ChecksumManagerTest.java
  config/ConfigurationManagerTest.java
```

#### [MODIFY] `README.md`
Full project documentation: architecture overview, setup guide, feature list, design patterns used, screenshots.

---

## Folder Structure (Final)

```
SecureVault-Pro/
├── pom.xml
├── config.properties
├── pipeline.json
├── schema.sql
└── src/
    ├── main/
    │   ├── java/com/securevault/
    │   │   ├── Main.java
    │   │   ├── auth/
    │   │   ├── encryption/
    │   │   ├── decorators/
    │   │   ├── task/
    │   │   ├── thread/
    │   │   ├── cache/
    │   │   ├── logger/
    │   │   ├── integrity/
    │   │   ├── analytics/
    │   │   ├── vault/
    │   │   ├── storage/
    │   │   ├── database/
    │   │   ├── config/
    │   │   ├── events/
    │   │   ├── exceptions/
    │   │   ├── util/
    │   │   └── ui/
    │   └── resources/
    │       ├── config.properties
    │       ├── pipeline.json
    │       ├── schema.sql
    │       ├── fxml/
    │       └── styles/
    └── test/
        └── java/com/securevault/
```

---

## Verification Plan

### Automated Tests
```bash
mvn test                          # Run all JUnit 5 tests
mvn javafx:run                    # Launch the application
```

### Manual Verification (per phase)
- **Phase 1**: `mvn compile` succeeds; DB tables auto-created on first run
- **Phase 2**: Register → Login → 5 wrong passwords → account locked message
- **Phase 3**: Upload file → encrypted copy exists → download → byte-for-byte match
- **Phase 4**: Submit 10 concurrent tasks → HIGH priority finishes first (verifiable in logs)
- **Phase 5**: Upload same file twice → cache hit logged on second access
- **Phase 6**: Full GUI walkthrough — login, upload, vault, analytics screen
- **Phase 7**: All unit tests green; `mvn site` generates Javadoc

---

## Build Order (Dependency Graph)

```
exceptions → util → config → database
                                ↓
                    auth (PasswordHasher → AuthenticationService)
                                ↓
                    encryption (Strategies → Factory)
                                ↓
                    decorators (Pipeline → PipelineBuilder)
                                ↓
                    integrity → storage (Buffered + NIO)
                                ↓
                    task + thread (PriorityBlockingQueue)
                                ↓
                    events (EventBus wires everything)
                                ↓
                    cache + analytics + logger
                                ↓
                    vault
                                ↓
                    ui (JavaFX, wires all services)
```
