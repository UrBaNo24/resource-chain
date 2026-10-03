# ChainResource - Generic Multi-Tier Caching Chain

A thread-safe, generic Java implementation of a chained resource manager (`ChainResource<T>`) designed to query, cache, and propagate values across multiple storage layers.

## Overview

The core component, `ChainResource<T>`, exposes a single method:

```java
public CompletableFuture<T> getValue()
```

The resource coordinates an ordered list of storages from **outermost** (fastest, nearest cache) to **innermost** (authoritative source of truth).

### Concrete Implementation: Exchange Rates
This implementation models exchange rates (`ExchangeRateList`) retrieved from the Open Exchange Rates API across three tiers:

1. **MemoryStorage (Outermost):** Read-write, in-memory cache with a 1-hour expiration.
2. **FileSystemStorage (Intermediate):** Read-write, persists data as JSON to disk with a 4-hour expiration. Expiration survives application restarts by inspecting file metadata (`Files.getLastModifiedTime`).
3. **WebServiceStorage (Innermost):** Read-only, queries `https://openexchangerates.org/api/latest.json`. No expiration.

---

## Key Design Decisions & Architecture

### 1. Single-Flight Concurrency (Thundering Herd Protection)
When multiple threads call `getValue()` concurrently during a cache miss or after expiration, querying the underlying source multiple times can overwhelm external services and degrade performance.

- Implemented using a lock-free **Single-Flight pattern** with `AtomicReference<CompletableFuture<T>>`.
- The first thread to encounter a cache miss atomically creates a pending `CompletableFuture<T>` and initiates asynchronous chain traversal.
- Concurrent callers join the exact same in-flight `CompletableFuture`.
- When the operation completes, all waiting threads are resolved simultaneously.
- A CAS retry loop guarantees thread-safety without blocking threads using traditional synchronized locks.
- Outermost cache hits return immediately via `CompletableFuture.completedFuture(...)` with zero overhead.

### 2. Sequential Traversal & Upward Propagation
- **Traversal:** Evaluated sequentially from index `0` to `N - 1`. If a storage is empty, expired, or throws an exception (e.g. temporary network issue or corrupt file), the chain continues to the next storage.
- **Upward Propagation:** Once a valid value is retrieved from index `k`, it is immediately written to all read-write storages above it (indices `k - 1` down to `0`).
- **Fault Tolerance:** Failures during upward writes (e.g. disk write permissions) are caught and ignored so that the client's read request still succeeds with the retrieved value.

### 3. Restart-Safe File Expiration
- Rather than maintaining volatile timestamps in memory for disk files, `FileSystemStorage` inspects the filesystem's `lastModifiedTime` metadata.
- When the application stops and starts again, any fresh instance of `FileSystemStorage` calculates:
  `Duration.between(lastModifiedInstant, Instant.now()) <= expiration (4 hours)`
- If valid, the file is read and deserialized from disk immediately, avoiding unnecessary remote network calls across restarts.

---

## Project Structure

```
src/
├── main/java/
│   ├── ChainResource.java         # Core generic resource coordinator
│   ├── Storage.java               # Storage interface (read, write, isReadOnly, getExpiration)
│   ├── MemoryStorage.java         # Thread-safe in-memory cache (1h expiration)
│   ├── FileSystemStorage.java     # JSON file persistence (4h expiration, restart-safe)
│   ├── WebServiceStorage.java     # HTTP client for Open Exchange Rates API
│   ├── ExchangeRateList.java      # Immutable record representing exchange rates
│   └── Main.java                  # Demonstration entry point
└── test/java/
    ├── ChainResourceTest.java     # Concurrency / single-flight and traversal tests
    ├── MemoryStorageTest.java     # In-memory expiration and storage tests
    ├── FileSystemStorageTest.java # Disk persistence, restart survival, and expiration tests
    └── WebServiceStorageTest.java # API client tests and configuration resolution
```

---

## Prerequisites

- **Java 21** (JDK 21)
- **Maven 3.8+**

---

## Running the Tests

To execute the full test suite (19 unit and concurrency tests):

```bash
mvn clean test
```

This runs:
- High-contention concurrency tests (50 concurrent threads requesting simultaneously resulting in 1 underlying call).
- Storage failure and chain fallback scenarios.
- Upward propagation across layers.
- File system persistence across simulated restarts.
- Expiration behavior for in-memory and disk layers.

---

## Running the Application

### 1. API Key Configuration
The Open Exchange Rates App ID is dynamically resolved without being hardcoded. You can provide it via any of the following methods:

**Option A - Program argument (recommended for IDE):**
Pass your App ID as the first program argument.

**Option B - Environment variable:**
```bash
# Linux/macOS
export OPEN_EXCHANGE_RATES_APP_ID="your_app_id"

# Windows (PowerShell)
$env:OPEN_EXCHANGE_RATES_APP_ID="your_app_id"
```

**Option C - System property:**
```bash
-Dopenexchangerates.app_id=your_app_id
```

### 2. Execute via Maven

```bash
mvn compile exec:java -D"exec.mainClass=Main" -D"exec.args=YOUR_APP_ID"
```

Or run `Main.java` directly from your IDE with your App ID set in **Program arguments**.
