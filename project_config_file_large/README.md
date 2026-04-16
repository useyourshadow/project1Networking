# Test config — project_config_file_large

This folder holds a **larger test configuration** for the P2P file-sharing project.

## Contents

- **Common.cfg** — 3 preferred neighbors, 5s unchoking, 10s optimistic unchoking; file `tree.jpg`, 24,301,474 bytes, piece size 16,384 (≈1484 pieces).
- **PeerInfo.cfg** — 6 peers (1001–1006) on hosts `lin114-00.cise.ufl.edu` … `lin114-05.cise.ufl.edu`, all listening on port **6001**.
- **1001/ … 1006/** — Per-peer directories (peer_<id> equivalent when running from this folder).

## Use with current code

The peer process reads `Common.cfg` and `PeerInfo.cfg` from the **current working directory**. To use this test config:

1. **Run from this folder:**
   ```bash
   cd project_config_file_large
   java -cp .. peerProcess 1001
   ```
   (Adjust `-cp` if your class files live elsewhere.)

2. **Single-machine testing:**  
   All 6 peers use port 6001 in the provided PeerInfo.cfg, so they are meant for **6 different hosts**. To run multiple peers on one machine, use a copy of PeerInfo.cfg where each peer has a **different port** (e.g. 6001–6006) and host `localhost`.

3. **Peer with file:**  
   Peer 1001 has `has_file = 1`. Ensure `1001/` (or `peer_1001/` if the program creates it under the CWD) actually contains `tree.jpg` before starting 1001.

## Ensuring current code is correct

- **Config parsing:** Run with this folder as CWD and confirm no errors; check that `numPieces` and file/piece sizes match Common.cfg.
- **Connections:** With multiple peers (different ports on one host or different hosts), confirm handshake and bitfield exchange and that logs match the expected format in the project spec.
- **Wire format:** The same Handshake, Message, and Util bitfield packing are used regardless of config; tests with the root config (small file, 3 peers) already validate wire format. This folder is for larger-file and multi-peer scenarios once file transfer and choking are implemented.
