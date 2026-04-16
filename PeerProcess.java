import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main peer process.
 *
 * Part A hook points (marked TODO_PART_A):
 *   - Preferred-neighbor selection timer (every unchokingInterval seconds)
 *   - Optimistic-unchoke timer (every optimisticUnchokingInterval seconds)
 *   Both timers iterate over `connections` and call handler.setChoked(boolean).
 */
public class PeerProcess {

    // Shared list of active connections — used for HAVE broadcast and Part A timers.
    // CopyOnWriteArrayList so timers can iterate safely without locking.
    private static final List<PeerConnectionThread> connections =
        new CopyOnWriteArrayList<>();

    // Tracks how many pieces each peer owns (updated when we receive HAVE from them).
    // Key: remotePeerId, Value: number of pieces that peer has reported having.
    // Part A uses this to detect when all peers are done.
    private static final Map<Integer, Integer> peerPieceCount =
        new ConcurrentHashMap<>();

    private static int totalNumPieces;
    private static int totalPeers;         
    private static Bitfield myBitfield;
    private static P2PLogger logger;
    private static volatile boolean running = true;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java PeerProcess <peerID>");
            return;
        }

        int myPeerId = Integer.parseInt(args[0]);
        System.out.println("Starting peer " + myPeerId + "...");

        try {
            CommonConfig config = CommonConfig.load("Common.cfg");
            List<PeerInfo> allPeers = PeerInfo.load("PeerInfo.cfg");
            totalNumPieces = config.numPieces;
            totalPeers     = allPeers.size();

            System.out.println("Config: " + config.numPieces + " pieces, "
                + "fileSize=" + config.fileSize + ", pieceSize=" + config.pieceSize);
            PeerInfo myInfo = null;
            List<PeerInfo> earlierPeers = new ArrayList<>();
            for (PeerInfo pi : allPeers) {
                if (pi.peerId == myPeerId) { myInfo = pi; break; }
                earlierPeers.add(pi);
            }
            if (myInfo == null) {
                System.out.println("Error: peer " + myPeerId + " not found in PeerInfo.cfg");
                return;
            }
            final PeerInfo myPeerInfo = myInfo;

            for (PeerInfo pi : allPeers) peerPieceCount.put(pi.peerId, 0);

            myBitfield = new Bitfield(config.numPieces);
            if (myInfo.hasFile) {
                for (int i = 0; i < config.numPieces; i++) myBitfield.setPiece(i);
                peerPieceCount.put(myPeerId, config.numPieces);
                System.out.println("Peer " + myPeerId + " has the complete file.");
            } else {
                System.out.println("Peer " + myPeerId + " does not have the file.");
            }

            FileManager fileManager = new FileManager(
                myPeerId, config.fileName, config.fileSize,
                config.pieceSize, config.numPieces);

            logger = new P2PLogger(myPeerId);

            final int myId = myPeerId;
            PieceDownloadCallback pieceCallback = (pieceIndex, myFileComplete) -> {
                byte[] havePayload = Util.intToBytes(pieceIndex);
                for (PeerConnectionThread pct : connections) {
                    ConnectionHandler h = pct.getHandler();
                    if (h == null) continue;
                    try {
                       
                        sendHave(pct, havePayload);
                    } catch (IOException e) {
                        System.out.println("Error sending HAVE to peer " + pct.getRemotePeerId());
                        e.printStackTrace();
                    }
                }

                if (myFileComplete) {
                    logger.logDownloadComplete();
                    peerPieceCount.put(myId, totalNumPieces);
                    checkAllDone();
                }
            };

            Thread serverThread = new Thread(() -> {
                try (ServerSocket ss = new ServerSocket(myPeerInfo.port)) {
                    System.out.println("Peer " + myId +
                        " listening on port " + myPeerInfo.port);
                    while (running) {
                        Socket clientSocket = ss.accept();
                        PeerConnectionThread pct = new PeerConnectionThread(
                            clientSocket, myId, myBitfield, logger,
                            config.numPieces, fileManager, pieceCallback);
                        connections.add(pct);
                        pct.start();
                    }
                } catch (IOException e) {
                    if (running) e.printStackTrace();
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            Thread.sleep(500);

            for (PeerInfo pi : earlierPeers) {
                int retries = 5;
                boolean connected = false;
                while (!connected && retries > 0) {
                    try {
                        System.out.println("Peer " + myId + " connecting to peer "
                            + pi.peerId + " at " + pi.hostname + ":" + pi.port);
                        Socket socket = new Socket(pi.hostname, pi.port);
                        PeerConnectionThread pct = new PeerConnectionThread(
                            socket, myId, pi.peerId, myBitfield, logger,
                            config.numPieces, fileManager, pieceCallback);
                        connections.add(pct);
                        pct.start();
                        connected = true;
                    } catch (ConnectException e) {
                        retries--;
                        System.out.println("  Peer " + pi.peerId
                            + " not ready, retrying in 2s (" + retries + " left)");
                        Thread.sleep(2000);
                    }
                }
                if (!connected) System.out.println("  Failed to connect to " + pi.peerId);
            }

            System.out.println("Peer " + myId + " startup complete.");

            // ----------------------------------------------------------------
            // TODO_PART_A: Start preferred-neighbor timer
            //
            // Every config.unchokingInterval seconds:
            //   1. Collect all handlers where handler.isRemoteInterested() == true
            //   2. If myBitfield.isComplete(): pick k random ones as preferred
            //      Else: sort by handler.getDownloadRate() desc, pick top k
            //            (break ties randomly)
            //   3. For each connection:
            //        if in preferred set AND handler.isChokingRemote() -> setChoked(false)
            //        if NOT in preferred set AND NOT optimistically unchoked
            //             AND NOT handler.isChokingRemote() -> setChoked(true)
            //   4. Call handler.resetDownloadRate() on all handlers
            //   5. logger.logPreferredNeighbors(List<Integer> ids)
            //
            // Use a ScheduledExecutorService:
            //   ScheduledExecutorService scheduler =
            //       Executors.newScheduledThreadPool(2);
            //   scheduler.scheduleAtFixedRate(preferredNeighborTask,
            //       config.unchokingInterval, config.unchokingInterval,
            //       TimeUnit.SECONDS);
            // ----------------------------------------------------------------

            // ----------------------------------------------------------------
            // TODO_PART_A: Start optimistic-unchoke timer
            //
            // Every config.optimisticUnchokingInterval seconds:
            //   1. Find all handlers where:
            //        handler.isRemoteInterested() == true
            //        AND handler.isChokingRemote() == true   (they're choked)
            //   2. Pick one randomly as the optimistically unchoked neighbor
            //   3. handler.setChoked(false)  on the chosen one
            //      (if they were already unchoked as a preferred neighbor, that's fine)
            //   4. logger.logOptimisticallyUnchokedNeighbor(remotePeerId)
            //   5. Remember which peer is currently optimistically unchoked so the
            //      preferred-neighbor timer above doesn't re-choke them.
            // ----------------------------------------------------------------

            // Main thread: just wait until shutdown signal.
            while (running) {
                Thread.sleep(1000);
            }

            fileManager.close();
            logger.close();
            System.out.println("Peer " + myId + " exiting.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Sends a HAVE message through the given connection thread.
     * We access the socket's output stream via the thread's handler.
     *
     * NOTE: ConnectionHandler.setChoked() is the model for how Part A sends
     * control messages.  Here we add a public sendHave() to PeerConnectionThread
     * for the HAVE broadcast.
     */
    private static void sendHave(PeerConnectionThread pct, byte[] havePayload)
            throws IOException {
        pct.sendHave(havePayload);
    }

    /**
     * Checks whether all known peers now have every piece.
     * If so, shuts down.
     *
     * This is a simple approximation: we know our own count, and we update
     * peerPieceCount when we receive HAVE messages — see TODO note below.
     *
     * TODO_PART_A / PART_B integration:
     *   When PeerConnectionThread receives a HAVE message from a remote peer,
     *   it should also call:
     *     PeerProcess.recordHaveFromPeer(remotePeerId);
     *   so we can track remote progress toward termination.
     *   Add a static method below and wire it into handleHave() in
     *   PeerConnectionThread (after calling handler.handleHave()).
     */
    private static void checkAllDone() {
        for (Map.Entry<Integer, Integer> entry : peerPieceCount.entrySet()) {
            if (entry.getValue() < totalNumPieces) return;
        }
        System.out.println("All peers have the complete file. Shutting down.");
        running = false;
    }

    /**
     * Called from PeerConnectionThread whenever it receives a HAVE from a remote peer.
     * Increments that peer's piece count; triggers shutdown if everyone is done.
     */
    public static void recordHaveFromPeer(int remotePeerId) {
        peerPieceCount.merge(remotePeerId, 1, Integer::sum);
        checkAllDone();
    }
}