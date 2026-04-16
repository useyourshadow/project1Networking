# Part A — Choking / Unchoking Engine
### Instructions for your teammate

---

## What Part B already did (your context)

Part B is complete. Here is what exists and how it connects to your work:

- **`ConnectionHandler`** — handles all message I/O for one peer connection. The key methods you will call:
  - `handler.setChoked(boolean)` — sends CHOKE or UNCHOKE to that peer
  - `handler.isRemoteInterested()` — true if that peer sent INTERESTED to us
  - `handler.isChokingRemote()` — true if we are currently choking them
  - `handler.getDownloadRate()` — bytes received from them this interval
  - `handler.resetDownloadRate()` — reset the counter (call this after each interval)
  - `handler.getRemotePeerId()` — their peer ID (for logging)

- **`PeerProcess`** — the main class. It has a static `List<PeerConnectionThread> connections` (CopyOnWriteArrayList) that you iterate to find all live connections. Each thread exposes `pct.getHandler()` which returns a `ConnectionHandler` (may be null briefly before handshake).

- **`P2PLogger`** — already has the two log methods you need:
  - `logger.logPreferredNeighbors(List<Integer> ids)`
  - `logger.logOptimisticallyUnchokedNeighbor(int neighborId)`

---

## Your task: implement two timers in `PeerProcess.main()`

The two `TODO_PART_A` comments in `PeerProcess.java` mark exactly where to add these. Use a `ScheduledExecutorService`:

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
```

---

### Timer 1 — Preferred Neighbors (every `config.unchokingInterval` seconds)

```
scheduler.scheduleAtFixedRate(preferredNeighborTask,
    config.unchokingInterval, config.unchokingInterval, TimeUnit.SECONDS);
```

**Algorithm:**

1. Collect all `ConnectionHandler` objects from `connections` where `handler != null`.

2. Filter to those where `handler.isRemoteInterested() == true`.

3. **Pick k preferred neighbors** (`k = config.numberOfPreferredNeighbors`):
   - If `myBitfield.isComplete()`: pick k at random from the interested set.
   - Else: sort by `handler.getDownloadRate()` descending; pick top k. Break ties randomly (shuffle equal-rate peers before picking).

4. **Send CHOKE / UNCHOKE** to every connection:
   - If in preferred set AND currently choking them → `handler.setChoked(false)`
   - If NOT in preferred set AND NOT the optimistically unchoked peer AND currently unchoked → `handler.setChoked(true)`
   - (Do nothing if state already matches.)

5. **Reset rates**: call `handler.resetDownloadRate()` on **all** handlers (not just preferred).

6. **Log**: `logger.logPreferredNeighbors(List<Integer> preferredIds)`

> **Note on the optimistically unchoked peer**: keep a `volatile int optimisticPeerId = -1` field in `PeerProcess`. The preferred-neighbor timer must NOT choke the optimistically unchoked peer even if they are not in the preferred set.

---

### Timer 2 — Optimistic Unchoke (every `config.optimisticUnchokingInterval` seconds)

```
scheduler.scheduleAtFixedRate(optimisticUnchokeTask,
    config.optimisticUnchokingInterval, config.optimisticUnchokingInterval,
    TimeUnit.SECONDS);
```

**Algorithm:**

1. Collect all `ConnectionHandler` objects where:
   - `handler.isRemoteInterested() == true`
   - `handler.isChokingRemote() == true`  (they are currently choked by us)

2. If the list is empty, do nothing.

3. Pick one at random. Call `handler.setChoked(false)`.

4. Update `optimisticPeerId` to this peer's remote ID.

5. Log: `logger.logOptimisticallyUnchokedNeighbor(remotePeerId)`

---

## Checklist

- [ ] `ScheduledExecutorService` created with 2 threads
- [ ] Preferred-neighbor timer fires every `unchokingInterval` seconds
- [ ] Optimistic unchoke timer fires every `optimisticUnchokingInterval` seconds
- [ ] Both timers wrapped in try/catch (a thrown exception kills a scheduled task silently)
- [ ] `optimisticPeerId` is checked in the preferred-neighbor timer so the optimistic peer is not re-choked
- [ ] `scheduler.shutdown()` called before `PeerProcess` exits (after `running` goes false)
- [ ] Logger calls match the format in `P2PLogger.java`

---

## Compile command (same as before, just add no new files)

```bash
javac MessageType.java Util.java Bitfield.java Message.java Handshake.java \
      CommonConfig.java PeerInfo.java P2PLogger.java FileManager.java \
      PieceDownloadCallback.java ConnectionHandler.java \
      PeerConnectionThread.java PeerProcess.java
```

---

## Quick sanity test

Run peers 1001–1003 locally (1001 has the file). After one unchoking interval you should see in the logs:

```
[time]: Peer 1002 has the preferred neighbors 1001.
[time]: Peer 1002 has the optimistically unchoked neighbor 1003.
```

And eventually:
```
[time]: Peer 1002 has downloaded the complete file.
[time]: Peer 1003 has downloaded the complete file.
```
