# Testing current functionality

Use **three terminals** (or IntelliJ + two terminals). Run everything from the **project root** (where `Common.cfg` and `PeerInfo.cfg` live).

---

## 1. Compile (if not using IntelliJ Build)

From the project root:

```bash
cd /Users/kaimcfarlane/2026_SPRING_COURSES/CNT4007/CNT_4007_GitRepo/project1Networking

javac CommonConfig.java PeerInfo.java Handshake.java Message.java MessageType.java Util.java Bitfield.java ConnectionHandler.java P2PLogger.java PeerConnectionThread.java PeerProcess.java
```

Or compile all Java files in one go:

```bash
javac *.java
```

---

## 2. Start the peers (order matters)

Peer 1001 must start first (others connect to it). Start 1002 only after 1001 is listening, then 1003.

**Terminal 1 — Peer 1001 (has file, listens on 6008):**

```bash
cd /Users/kaimcfarlane/2026_SPRING_COURSES/CNT4007/CNT_4007_GitRepo/project1Networking
java PeerProcess 1001
```

Wait until you see: `Peer 1001 listening on port 6008` and `Peer 1001 startup complete.`

**Terminal 2 — Peer 1002 (no file, listens 6009, connects to 1001):**

```bash
cd /Users/kaimcfarlane/2026_SPRING_COURSES/CNT4007/CNT_4007_GitRepo/project1Networking
java PeerProcess 1002
```

**Terminal 3 — Peer 1003 (no file, listens 6010, connects to 1001 and 1002):**

```bash
cd /Users/kaimcfarlane/2026_SPRING_COURSES/CNT4007/CNT_4007_GitRepo/project1Networking
java PeerProcess 1003
```

---

## 3. What you should see (current behavior)

- **1001:** Loads config, creates `peer_1001/`, listens on 6008. When 1002 and 1003 connect: handshakes, sends bitfield (has all pieces), receives INTERESTED from both, logs “connected from” and “received interested”.
- **1002:** Connects to 1001, handshake, receives bitfield, sends INTERESTED (wants pieces). Logs “makes a connection to Peer 1001”, “unchoked/choked” only if we ever send those (currently no choking logic, so you may not see choke/unchoke).
- **1003:** Connects to 1001 and 1002; handshakes with both; gets bitfield from 1001 (and optionally 1002 if 1002 had sent one — 1002 has no pieces so skips bitfield); sends INTERESTED to 1001.

Console output will show lines like:

- `Starting peer 1001...` / `Config loaded: 306 pieces...`
- `Peer 1001 listening on port 6008`
- `Peer 1001 startup complete.`
- `Peer 1002: Handshake completed with peer 1001`
- `Peer 1001: Handshake completed with peer 1002`
- `Peer 1001: Sent bitfield to peer 1002` (and similar for 1003)
- `Peer 1002: Sent INTERESTED to peer 1001`

---

## 4. Check log files

In the project root you should see:

- `log_peer_1001.log`
- `log_peer_1002.log`
- `log_peer_1003.log`

**Example checks:**

```bash
# Connection events (1001 sees incoming; 1002/1003 see outgoing)
cat log_peer_1001.log | grep -E "connection|connected from"
cat log_peer_1002.log | grep "makes a connection"
cat log_peer_1003.log | grep "makes a connection"

# Interest (1001 should see “received the 'interested' message” from 1002 and 1003)
cat log_peer_1001.log | grep "interested"
```

You should see at least:

- **1001:** “Peer 1001 is connected from Peer 1002.” and “Peer 1001 is connected from Peer 1003.” and “received the 'interested' message from 1002” and “from 1003”.
- **1002:** “Peer 1002 makes a connection to Peer 1001.”
- **1003:** “Peer 1003 makes a connection to Peer 1001.” and “Peer 1003 makes a connection to Peer 1002.”

---

## 5. Stop the peers

In each terminal: **Ctrl+C**. The main loop runs until you stop it; there is no “all peers have file” exit yet.

---

## Quick copy-paste summary

| Terminal | Command |
|----------|--------|
| 1 | `cd /Users/kaimcfarlane/2026_SPRING_COURSES/CNT4007/CNT_4007_GitRepo/project1Networking` then `java PeerProcess 1001` |
| 2 | Same `cd`, then `java PeerProcess 1002` |
| 3 | Same `cd`, then `java PeerProcess 1003` |

Run 1001 first, wait for “startup complete”, then 1002, then 1003.
