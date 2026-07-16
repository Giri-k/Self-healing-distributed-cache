# Self-Healing Distributed Cache

A from-scratch, sharded, replicated key-value cache cluster that survives node failure live. Built in Java 21 with Spring Boot, gRPC, and consistent hashing.

## Architecture

```
client --> any node (coordinator) --> consistent-hash ring lookup --> forward to primary
                                                                 --> replicate to N-1 replicas
```

- **Leaderless** — no single point of failure for routing. Any node accepts requests and forwards to the correct owner.
- **3 nodes** by default, configurable. Each node runs storage, gossip, and routing concurrently.
- **gRPC** for all inter-node communication (cache ops, replication, pings, key streaming).

## Core Features

| Feature | Implementation |
|---|---|
| **Consistent Hashing** | SHA-1 hash ring with 150 virtual nodes per physical node. Only ~1/N keys remap on membership change. |
| **Replication** | Synchronous write-through to N-1 replicas (configurable factor). Version-checked writes reject stale data. |
| **Failure Detection** | SWIM gossip protocol — each node pings one random peer/second. Direct ping + indirect probe before marking suspected. Confirmed dead after 5s timeout. |
| **Zero-Downtime Rebalancing** | On node join/return, only affected keys stream to new owner. Reads fall back to replicas during migration. |
| **TTL Expiry** | Absolute timestamps (not relative TTL) survive replication across nodes. Lazy expiry on read + background sweep every 10s. |
| **Chaos Tested** | 99.91% success rate across 864K requests with random node kills every 10-15s. p99 latency: 2ms. |

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.5
- **RPC**: gRPC + Protocol Buffers
- **Build**: Maven
- **Deployment**: Docker Compose (3-node cluster)

## Project Structure

```
src/main/java/com/cache/
  storage/        StorageEngine, CacheEntry (ConcurrentHashMap + TTL)
  ring/           ConsistentHashRing (TreeMap, SHA-1, virtual nodes)
  cluster/        ClusterService (coordinator), RebalanceService
  gossip/         GossipService (SWIM), MembershipList (ALIVE/SUSPECTED/DEAD)
  grpc/           CacheGrpcService (gRPC handlers)
  config/         NodeConfig (Spring @Value bindings)
  chaos/          ChaosLoadDriver (load + latency measurement)

src/main/proto/
  cache.proto     Get, Set, Delete, Replicate, Ping, IndirectPing, StreamKeys

chaos-test.sh     Bash script that randomly kills/restarts nodes
```

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Compose

### Build and Run

```bash
# Build
mvn clean package -DskipTests

# Start 3-node cluster
docker compose build
docker compose up
```

### Test with grpcurl

```bash
# Set a key
grpcurl -plaintext \
  -d '{"key":"user:1","value":"alice","ttl_seconds":60}' \
  localhost:9090 cache.CacheService/Set

# Get a key
grpcurl -plaintext \
  -d '{"key":"user:1"}' \
  localhost:9090 cache.CacheService/Get

# Delete a key
grpcurl -plaintext \
  -d '{"key":"user:1"}' \
  localhost:9090 cache.CacheService/Delete
```

### Run Chaos Test

Three terminals:

```bash
# Terminal 1: Start cluster
docker compose up

# Terminal 2: Start load driver (60s, 10 threads)
mvn compile exec:java

# Terminal 3: Start chaos (kills random nodes)
./chaos-test.sh
```

### Run Unit Tests

```bash
mvn test
```

15 tests covering storage engine, consistent hash ring, TTL expiry, and Spring context.

## Configuration

| Property | Default | Description |
|---|---|---|
| `cache.node-id` | `node-1` | Unique node identifier |
| `cache.peers` | — | Comma-separated `id:host:port` list |
| `cache.virtual-nodes` | `150` | Virtual nodes per physical node on the hash ring |
| `cache.replication-factor` | `2` | Number of copies (primary + replicas) |
| `cache.ping-interval-ms` | `1000` | Gossip ping frequency |
| `cache.ping-timeout-ms` | `500` | Direct ping timeout |
| `cache.suspect-timeout-ms` | `5000` | Time before suspected node is confirmed dead |

## Chaos Test Results

```
========= CHAOS TEST RESULTS =========
Total requests: 864,374
Successes:      863,600
Failures:       774
Retries:        800
Success rate:   99.91%

Latency:
  p50: 0ms
  p99: 2ms
  max: 302ms
======================================
```

5 node kills over 60 seconds with 10 concurrent load threads.
