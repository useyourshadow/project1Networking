# P2P File Sharing — Architecture & Status

**Project:** CNT 4007 — BitTorrent-style P2P with choking/unchoking  
**Entry point:** `java peerProcess <peerID>`

---

## 1. Architecture Overview

### 1.1 High-level component diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PeerProcess (main)                                 │
│  • Load Common.cfg, PeerInfo.cfg                                             │
│  • Build local Bitfield (all 1s if has file, else all 0s)                   │
│  • Create peer_<id>/ dir                                                    │
│  • Start server thread (accept incoming TCP)                                 │
│  • Connect to all “earlier” peers (lower ID in PeerInfo.cfg)                │
│  • Run forever (no termination logic yet)                                    │
└─────────────────────────────────────────────────────────────────────────────┘
         │                                    │
         │ spawns                              │ spawns
         ▼                                    ▼
┌─────────────────────────────┐    ┌─────────────────────────────┐
│   ServerSocket.accept()     │    │   Socket(host, port)         │
│   (incoming connection)     │    │   (outgoing connection)       │
└──────────────┬──────────────┘    └──────────────┬──────────────┘
               │                                  │
               └──────────────┬───────────────────┘
                              ▼
               ┌─────────────────────────────┐
               │   PeerConnectionThread      │
               │   (one per TCP connection)  │
               │   • Handshake                │
               │   • Send/recv Bitfield       │
               │   • Message loop             │
               └──────────────┬──────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Handshake       │  │ Message         │  │ ConnectionHandler│
│ (32-byte)       │  │ (len+type+pay)  │  │ (state per conn) │
└─────────────────┘  └─────────────────┘  └─────────────────┘
         │                    │                    │
         │                    │                    │ uses
         │                    │                    ▼
         │                    │           ┌─────────────────┐
         │                    │           │ Bitfield        │
         │                    │           │ (local pieces)  │
         │                    │           └─────────────────┘
         │                    │
         │                    │           ┌─────────────────┐
         └────────────────────┴───────────│ Util            │
                      uses                │ P2PLogger       │
                                          └─────────────────┘
```

### 1.2 Message flow (single connection)

```
Peer A                                    Peer B
   │                                         │
   │  ─────── TCP connect ─────────────────► │
   │  ◄────── accept ─────────────────────── │
   │                                         │
   │  ─────── Handshake (32 bytes) ────────► │
   │  ◄────── Handshake (32 bytes) ───────── │
   │                                         │
   │  ─────── Bitfield (if has pieces) ────► │
   │  ◄────── Bitfield (if has pieces) ───── │
   │                                         │
   │  ─────── INTERESTED / NOT_INTERESTED ─► │
   │  ◄────── INTERESTED / NOT_INTERESTED ── │
   │                                         │
   │  … CHOKE / UNCHOKE / HAVE / REQUEST / PIECE …  (partially implemented)
   │                                         │
```

### 1.3 File layout

```
project1Networking/
├── PeerProcess.java          # Main: config load, server, connect to earlier peers
├── PeerConnectionThread.java # Per-connection: handshake, bitfield, message loop
├── ConnectionHandler.java    # Handles bitfield/interested/have (no wire send)
├── Bitfield.java             # Local piece set (hasPiece, setPiece, isInterested)
├── Handshake.java            # 32-byte handshake build/parse
├── Message.java              # Length-prefixed message send/receive
├── MessageType.java          # CHOKE=0 … PIECE=7
├── Util.java                 # int↔bytes, pack/unpack bitfield (protocol format)
├── CommonConfig.java         # Parse Common.cfg
├── PeerInfo.java             # Parse PeerInfo.cfg
├── P2PLogger.java            # All required log lines to log_peer_<id>.log
├── Common.cfg                # Preferred neighbors, intervals, file/piece size
├── PeerInfo.cfg              # peer_id host port has_file
├── peer_<id>/                # Per-peer file storage (created at startup)
├── log_peer_<id>.log         # Per-peer log
│
└── project_config_file_large/   # Test config (multi-host / large file)
    ├── Common.cfg                # tree.jpg, 24301474 bytes, 16384 piece size
    ├── PeerInfo.cfg              # 6 peers, lin114-00..05, port 6001 each
    └── 1001/ … 1006/             # Peer subdirs (for running from this folder)
```

---

## 2. What has been done

### 2.1 Configuration

| Item | Status | Notes |
|------|--------|--------|
| Read `Common.cfg` | ✅ | CommonConfig: preferred neighbors, intervals, fileName, fileSize, pieceSize, numPieces |
| Read `PeerInfo.cfg` | ✅ | PeerInfo.load(): peerId, hostname, port, hasFile |
| Use `peer_<id>/` for files | ✅ | Directory created in PeerProcess; no file I/O yet |

### 2.2 Connection setup

| Item | Status | Notes |
|------|--------|--------|
| Listen on configured port | ✅ | Server thread in PeerProcess |
| Connect to all earlier peers | ✅ | By order in PeerInfo.cfg; retries with 2s delay |
| One thread per connection | ✅ | PeerConnectionThread per Socket |

### 2.3 Protocol — Handshake & bitfield

| Item | Status | Notes |
|------|--------|--------|
| Send/receive 32-byte handshake | ✅ | Handshake.toByteArray / fromByteArray |
| Header `P2PFILESHARINGPROJ` + 10 zero bytes + 4-byte peer ID | ✅ | Handshake.java |
| Validate peer ID on initiator side | ✅ | PeerConnectionThread.performHandshake |
| Send bitfield after handshake (if has any piece) | ✅ | sendBitfieldIfNeeded, Util.packBitfield |
| Bitfield wire format (high bit = piece 0) | ✅ | Util.packBitfield / unpackBitfield |
| Skip bitfield when no pieces | ✅ | Peers with empty bitfield don’t send it |

### 2.4 Protocol — Actual messages (length + type + payload)

| Item | Status | Notes |
|------|--------|--------|
| Send/receive length-prefixed messages | ✅ | Message.send / Message.receive |
| CHOKE / UNCHOKE / INTERESTED / NOT_INTERESTED | ✅ | Sent/received; CHOKE/UNCHOKE only logged |
| HAVE (4-byte piece index) | ✅ | Parsed with Util.bytesToInt; handler + log |
| BITFIELD (first message after handshake) | ✅ | Unpack with Util.unpackBitfield; INTERESTED/NOT_INTERESTED response |
| REQUEST (4-byte piece index) | ❌ | Not handled |
| PIECE (4-byte index + content) | ❌ | Not handled |

### 2.5 Interest

| Item | Status | Notes |
|------|--------|--------|
| Send INTERESTED after neighbor’s bitfield if neighbor has wanted pieces | ✅ | handleBitfieldMessage |
| Send NOT_INTERESTED when no wanted pieces | ✅ | Same path |
| Update neighbor state on HAVE | ✅ | ConnectionHandler.handleHave (state only; no wire send from handler) |

### 2.6 Logging

| Item | Status | Notes |
|------|--------|--------|
| TCP connection made / connected from | ✅ | P2PLogger.logMadeConnection / logConnectedFrom |
| Preferred neighbors change | ✅ | logPreferredNeighbors (not invoked yet) |
| Optimistically unchoked neighbor change | ✅ | logOptimisticallyUnchokedNeighbor (not invoked yet) |
| Unchoked by / Choked by | ✅ | logUnchokedBy / logChokedBy |
| Received HAVE / INTERESTED / NOT_INTERESTED | ✅ | logReceivedHave, logReceivedInterested, logReceivedNotInterested |
| Downloaded piece / download complete | ✅ | logDownloadedPiece, logDownloadComplete (not invoked yet) |
| Log file `log_peer_<id>.log` | ✅ | P2PLogger writes to file + stdout |

### 2.7 Test / config assets

| Item | Status | Notes |
|------|--------|--------|
| Root config (small file, 3 peers) | ✅ | Common.cfg, PeerInfo.cfg — localhost:6008,6009,6010 |
| Large/test config folder | ✅ | project_config_file_large/: Common.cfg, PeerInfo.cfg, 1001–1006 dirs |

---

## 3. What still needs to be done

### 3.1 File I/O

- **Read pieces from disk** for peers that have the file (when responding to REQUEST).
- **Write received pieces to disk** in `peer_<id>/` (e.g. under `FileName` or temp then rename).
- **Handle last piece size** (smaller than PieceSize) when reading/writing.

### 3.2 Request / piece exchange

- **Handle REQUEST:** look up piece index, read from file, send PIECE message.
- **Handle PIECE:** write payload to file, update local Bitfield, send HAVE to all neighbors, log download, check for download complete.
- **When unchoked:** pick a random piece that the neighbor has and we don’t, and that we haven’t requested elsewhere; send REQUEST; wait for PIECE before next REQUEST (no pipelining).
- **Track “requested but not yet received”** per neighbor so we don’t request the same piece from multiple peers (or handle re-request after choke).

### 3.3 Choking / unchoking (upload side)

- **Preferred neighbors (every UnchokingInterval):** among neighbors that are *interested*, choose `k` with highest *downloading rate* (bytes received from them in last interval); if we have complete file, choose randomly. Send UNCHOKE to selected, CHOKE to others (except optimistically unchoked).
- **Optimistic unchoke (every OptimisticUnchokingInterval):** among *choked* but *interested* neighbors, pick one at random; send UNCHOKE. Allow same peer to be both preferred and optimistically unchoked.
- **Rate tracking:** per-neighbor byte count and interval timers; clear or rotate each interval.
- **Only respond to REQUEST from unchoked neighbors** (ignore REQUEST when peer is choked).

### 3.4 Termination

- **Detect when all peers have the complete file** (e.g. global view or agreed protocol).
- **Exit process cleanly** (close sockets, logger, then System.exit or break main loop).

### 3.5 ConnectionHandler (constraints)

- **Do not change ConnectionHandler.java / Bitfield.java** per your request.
- ConnectionHandler currently has no reference to `DataOutputStream`; its `sendMessage()` only prints. So any “send” logic that belongs in the handler would need to live in PeerConnectionThread (or another class that has the stream) and call into the handler for state only.

### 3.6 Correctness vs test folder

- **project_config_file_large:** 6 peers, all port 6001 — intended for 6 different hosts (e.g. lin114-00 … lin114-05). For single-machine testing, use the root `PeerInfo.cfg` (localhost with 6008, 6009, 6010).
- **Using test folder:** run from `project_config_file_large/` so `Common.cfg` and `PeerInfo.cfg` are in the CWD, and ensure `peer_1001/` … `peer_1006/` exist (e.g. create if missing). For one host, you’d need to change PeerInfo.cfg to use different ports per peer.

---

## 4. Quick reference — protocol

| Message   | Type | Payload              | When |
|----------|------|----------------------|------|
| handshake| —    | 18 header + 10 zeros + 4 peerId | First on connection |
| bitfield | 5    | packed bits (piece 0 = high bit of first byte) | First actual message if sender has any piece |
| choke    | 0    | none                 | Uploader chokes peer |
| unchoke  | 1    | none                 | Uploader unchokes peer |
| interested | 2  | none                 | Peer has pieces we want |
| not interested | 3 | none               | Peer has no pieces we want |
| have     | 4    | 4-byte piece index   | After completing a piece |
| request  | 6    | 4-byte piece index   | Request one piece (only when unchoked) |
| piece    | 7    | 4-byte index + content | Response to request |

---

## 5. How to run

**Single-machine (current root config):**

```bash
# Terminal 1
java peerProcess 1001

# Terminal 2
java peerProcess 1002

# Terminal 3
java peerProcess 1003
```

**With test/large config:** run from `project_config_file_large/` and use the same command; ensure that folder contains `Common.cfg`, `PeerInfo.cfg`, and that the peer with the file has the file in its `peer_<id>/` directory. For one host, give each peer a different port in `PeerInfo.cfg`.

---

*Document generated for sharing architecture, current implementation, and remaining work. ConnectionHandler.java and Bitfield.java are left unchanged.*
