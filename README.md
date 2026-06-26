# SecureVault Pro — Secure File Storage & Password Vault

SecureVault Pro is an enterprise-grade desktop security application built using **Java 17** and **JavaFX 21**. It provides two core functions: secure, high-performance file encryption/decryption, and a credential vault for managing accounts and passwords. 

The project is designed to demonstrate industry-standard practices and technical depth across OOP, concurrency, DSA, database management, and cryptography—ideal for technical interview discussions.

---

## Key Features & Highlights

### 🛡️ 1. Cryptography & Security System
*   **PBKDF2 Key Derivation:** Uses `PBKDF2WithHmacSHA256` with **310,000 iterations** (OWASP recommendation) to derive a 256-bit AES master key from the user's password and a unique cryptographically random salt.
*   **AES-256-GCM Encryption:** Credentials and files are encrypted using Advanced Encryption Standard (AES) in Galois/Counter Mode (GCM). GCM provides **authenticated encryption**, meaning it protects both confidentiality *and* detects unauthorized tampering (via the 128-bit authentication tag).
*   **In-Memory Security (Key Zeroization):** Sensitive keys and decrypted credentials are held in raw byte arrays instead of immutable `String` objects. These arrays are explicitly overwritten with zeros (`Arrays.fill(..., (byte)0)`) immediately after use to prevent cryptographic keys from lingering in memory or appearing in JVM heap dumps.
*   **Account Locking Mechanism:** Implements brute-force protection. After **5 failed login attempts**, the account is locked in the database and requires admin resetting.

### 🚀 2. High-Performance I/O Pipeline (NIO)
*   **Dual-Mode Streaming:**
    *   *Small Files (< 10 MB):* Read and written using `BufferedInputStream` and `BufferedOutputStream` for simple heap-based streaming.
    *   *Large Files (≥ 10 MB):* Routinely processed using Java **NIO (New I/O) `FileChannel`** and off-heap direct `ByteBuffer`. This allows direct DMA (Direct Memory Access) transfers between disk and buffers without copying bytes into the JVM garbage-collected heap, reducing memory pressure and latency.
*   **Configurable Pipeline (Decorator Pattern):** File processing uses a chain of decorators (e.g., reversing bytes, XOR transformations, compressing, encrypting) configured via `pipeline.json` and constructed dynamically at runtime.

### 🧵 3. Custom Concurrency & Priority Scheduling
*   **Priority Thread Pool:** Powered by an `ExecutorService` backed by a `PriorityBlockingQueue`.
*   **Priority Preemption:** Submitted tasks implement `Comparable` based on `TaskPriority` (HIGH, NORMAL, LOW). Workers pull tasks based on priority, allowing urgent operations (like downloading a file) to leapfrog background tasks (like bulk analytics or logging).

### ⚡ 4. Advanced Data Structures & Caching (DSA)
*   **Least Recently Used (LRU) Metadata Cache:** Uses an access-ordered `LinkedHashMap` protected by a `ReentrantReadWriteLock` (supporting concurrent reads and single-writer isolation). Keyed by file content checksum (SHA-256) rather than filename, meaning cached metadata survives file renames.
*   **TreeMap Sorted Search:** Searching credentials performs a linear scan on keywords and aggregates matches in a `TreeMap` sorted alphabetically by website name, guaranteeing $O(\log N)$ inserts and automatically sorted display.
*   **PriorityQueue Bottleneck Analysis:** The analytics engine processes database metrics and loads per-algorithm stats into a custom `PriorityQueue` ordered slowest-first, isolating performance bottlenecks.

---

## Software Architecture & Design Patterns

The codebase is built on strict SOLID principles and utilizes several core design patterns:

| Pattern | Role in SecureVault Pro |
|---|---|
| **Strategy** | `EncryptionStrategy` defines the contract; `AESStrategy`, `XORStrategy`, and `CaesarStrategy` implement specific algorithms selected dynamically. |
| **Factory** | `EncryptionFactory` instantiates strategies based on user preference or config keys, promoting Open-Closed Principle (OCP). |
| **Decorator** | `EncryptionDecorator` and its subclasses chain multiple byte transformations (e.g., reversing, shifting, compressing) dynamically. |
| **Singleton** | Ensures single instances for `ConfigurationManager`, `DatabaseManager`, `EventBus`, `SessionManager`, and `AnalyticsService` to avoid connection leaks and race conditions. |
| **Observer** | Clean decoupling using a central `EventBus` pub-sub model. The `AuditLogger` and `AnalyticsService` listen for file, task, and auth events automatically without explicit coupling in core services. |

---

## Technology Stack

*   **Language:** Java 17 (utilizing modern features like Records, Text Blocks, and Pattern Matching).
*   **Database:** MySQL 8.0 (Fallback ready, schema auto-bootstraps on first connection).
*   **GUI Library:** JavaFX 21.0.2 with a premium Dark Glassmorphism CSS layout.
*   **Testing:** JUnit 5 for unit testing.
*   **Build Tool:** Maven 3.9.

---

## Directory Structure

```
SecureVault-Pro/
├── pom.xml                      # Maven project configuration
├── schema.sql                   # Database table definitions
├── src/
│   ├── main/
│   │   ├── java/com/securevault/
│   │   │   ├── Main.java         # Bootstrap non-Application launcher
│   │   │   ├── auth/            # Users, PBKDF2 hashing, and SessionManager
│   │   │   ├── config/          # ConfigurationManager Singleton
│   │   │   ├── database/        # DatabaseManager Singleton
│   │   │   ├── decorators/      # Decorator pipeline and PipelineBuilder
│   │   │   ├── encryption/      # Strategy and Factory encryption pattern
│   │   │   ├── events/          # EventBus pub-sub observer system
│   │   │   ├── exceptions/      # Custom domain exceptions
│   │   │   ├── integrity/       # SHA-256 checksums
│   │   │   ├── logger/          # Concurrent-queued AuditLogger
│   │   │   ├── storage/         # Buffered + NIO FileStorageService
│   │   │   ├── task/            # FileTask and TaskPriority models
│   │   │   ├── thread/          # Priority thread pool manager
│   │   │   ├── ui/              # JavaFX MainApp and View Controllers
│   │   │   └── vault/           # Credential model and PasswordVaultService
│   │   └── resources/
│   │       ├── config.properties# Runtime configurations (threads, buffer, etc.)
│   │       ├── pipeline.json    # Pipeline custom decorator chains
│   │       ├── schema.sql       # Copy of DB schema for bootstrapping
│   │       ├── fxml/            # JavaFX views (login, dashboard, vault, etc.)
│   │       └── styles/          # Styling CSS (dark-theme.css)
│   └── test/
│       └── java/com/securevault/# JUnit 5 unit tests for core services
```

---

## Setup & Execution

### Prerequisites
1. **Java Development Kit (JDK) 17** or higher.
2. **Maven 3.9** or higher.
3. **MySQL Server 8.0** running locally.

### 1. Database Configuration
Ensure MySQL is running and create the schema. You can run the schema script manually, or the application's `DatabaseManager` will auto-bootstrap it on first connection:
```sql
CREATE DATABASE IF NOT EXISTS securevault;
```
Configure your credentials in `src/main/resources/config.properties`:
```properties
db.url=jdbc:mysql://localhost:3306/securevault?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=your_mysql_password
```

### 2. Compilation
Compile and build the project using Maven:
```bash
mvn clean compile
```

### 3. Run Unit Tests
Run all JUnit 5 unit tests to verify the integrity and security layers:
```bash
mvn test
```

### 4. Launch the GUI
Launch the desktop application:
```bash
mvn javafx:run
```
