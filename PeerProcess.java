import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Main peer process: mesh TCP, choking, termination when all peers complete the file. */
public class PeerProcess {

    // Shared list of active connections — used for HAVE broadcast and Part A timers.
    // CopyOnWriteArrayList so timers can iterate safely without locking.
    private static final List<PeerConnectionThread> connections =
        new CopyOnWriteArrayList<>();

    // Tracks how many pieces each neighbor is known to have (BITFIELD snapshot + HAVE increments).
    private static final Map<Integer, Integer> peerPieceCount =
        new ConcurrentHashMap<>();

    /** Piece indices we have REQUEST in flight to any neighbor (spec: no duplicate requests). */
    private static final Set<Integer> inflightPieceRequests =
        ConcurrentHashMap.newKeySet();

    private static int totalNumPieces;
    private static Bitfield myBitfield;
    private static P2PLogger logger;
    private static volatile boolean running = true;

    /** Remote peer ID optimistically unchoked; preferred-neighbor pass must not re-choke them. */
    private static volatile int optimisticPeerId = -1;

    private static volatile ServerSocket listenSocket;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java PeerProcess <peerID>");
            return;
        }

        int myPeerId = Integer.parseInt(args[0]);
        System.out.println("Starting peer " + myPeerId + "...");

        ScheduledExecutorService scheduler = null;
        try {
            CommonConfig config = CommonConfig.load("Common.cfg");
            List<PeerInfo> allPeers = PeerInfo.load("PeerInfo.cfg");
            totalNumPieces = config.numPieces;

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
                try {
                    listenSocket = new ServerSocket(myPeerInfo.port);
                    System.out.println("Peer " + myId +
                        " listening on port " + myPeerInfo.port);
                    while (running) {
                        try {
                            Socket clientSocket = listenSocket.accept();
                            PeerConnectionThread pct = new PeerConnectionThread(
                                clientSocket, myId, myBitfield, logger,
                                config.numPieces, fileManager, pieceCallback);
                            connections.add(pct);
                            pct.start();
                        } catch (SocketException e) {
                            if (!running) break;
                            throw e;
                        }
                    }
                } catch (IOException e) {
                    if (running) e.printStackTrace();
                } finally {
                    if (listenSocket != null) {
                        try { listenSocket.close(); } catch (IOException ignored) {}
                    }
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

            final int prefK = config.numberOfPreferredNeighbors;
            Runnable preferredNeighborTask = () -> {
                try {
                    List<ConnectionHandler> all = new ArrayList<>();
                    for (PeerConnectionThread pct : connections) {
                        ConnectionHandler h = pct.getHandler();
                        if (h != null) {
                            all.add(h);
                        }
                    }
                    List<ConnectionHandler> interested = new ArrayList<>();
                    for (ConnectionHandler h : all) {
                        if (h.isRemoteInterested()) {
                            interested.add(h);
                        }
                    }
                    int opt = optimisticPeerId;
                    List<ConnectionHandler> preferredList = new ArrayList<>();
                    if (!interested.isEmpty()) {
                        int take = Math.min(prefK, interested.size());
                        if (myBitfield.isComplete()) {
                            Collections.shuffle(interested);
                            preferredList.addAll(
                                interested.subList(0, take));
                        } else {
                            Collections.shuffle(interested);
                            interested.sort(Comparator.comparingLong(
                                ConnectionHandler::getDownloadRate).reversed());
                            preferredList.addAll(
                                interested.subList(0, take));
                        }
                    }
                    Set<Integer> preferredSet = new HashSet<>();
                    List<Integer> preferredIds = new ArrayList<>();
                    for (ConnectionHandler h : preferredList) {
                        int rid = h.getRemotePeerId();
                        preferredSet.add(rid);
                        preferredIds.add(rid);
                    }
                    Collections.sort(preferredIds);
                    for (ConnectionHandler h : all) {
                        int rid = h.getRemotePeerId();
                        boolean inPref = preferredSet.contains(rid);
                        try {
                            if (inPref && h.isChokingRemote()) {
                                h.setChoked(false);
                            } else if (!inPref && rid != opt
                                && !h.isChokingRemote()) {
                                h.setChoked(true);
                            }
                        } catch (IOException e) {
                            System.out.println(
                                "Choke/unchoke error for peer " + rid + ": "
                                    + e.getMessage());
                        }
                    }
                    for (ConnectionHandler h : all) {
                        h.resetDownloadRate();
                    }
                    logger.logPreferredNeighbors(preferredIds);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };

            Runnable optimisticUnchokeTask = () -> {
                try {
                    List<ConnectionHandler> candidates = new ArrayList<>();
                    for (PeerConnectionThread pct : connections) {
                        ConnectionHandler h = pct.getHandler();
                        if (h != null && h.isRemoteInterested()
                            && h.isChokingRemote()) {
                            candidates.add(h);
                        }
                    }
                    if (candidates.isEmpty()) {
                        return;
                    }
                    ConnectionHandler chosen = candidates.get(
                        new Random().nextInt(candidates.size()));
                    int rid = chosen.getRemotePeerId();
                    try {
                        chosen.setChoked(false);
                    } catch (IOException e) {
                        System.out.println(
                            "Optimistic unchoke error for peer " + rid + ": "
                                + e.getMessage());
                        return;
                    }
                    optimisticPeerId = rid;
                    logger.logOptimisticallyUnchokedNeighbor(rid);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };

            scheduler = Executors.newScheduledThreadPool(2);
            scheduler.scheduleAtFixedRate(preferredNeighborTask,
                config.unchokingInterval, config.unchokingInterval,
                TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(optimisticUnchokeTask,
                config.optimisticUnchokingInterval,
                config.optimisticUnchokingInterval,
                TimeUnit.SECONDS);

            // Main thread: just wait until shutdown signal.
            while (running) {
                Thread.sleep(1000);
            }

            shutdownNetworking();

            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException ie) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
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
     */
    private static void sendHave(PeerConnectionThread pct, byte[] havePayload)
            throws IOException {
        pct.sendHave(havePayload);
    }

    public static boolean isRunning() {
        return running;
    }

    /** After a BITFIELD, set known piece count for that neighbor (covers seed peers with no per-piece HAVE). */
    public static void recordNeighborPieceCount(int remotePeerId, int piecesKnown) {
        peerPieceCount.put(remotePeerId, piecesKnown);
        checkAllDone();
    }

    public static boolean isInflightRequestRegistered(int pieceIndex) {
        return inflightPieceRequests.contains(pieceIndex);
    }

    /** True if this piece was not yet registered (caller will send REQUEST next). */
    public static boolean tryRegisterInflightRequest(int pieceIndex) {
        return inflightPieceRequests.add(pieceIndex);
    }

    public static void releaseInflightRequest(int pieceIndex) {
        inflightPieceRequests.remove(pieceIndex);
    }

    private static void checkAllDone() {
        for (Map.Entry<Integer, Integer> entry : peerPieceCount.entrySet()) {
            if (entry.getValue() < totalNumPieces) return;
        }
        System.out.println("All peers have the complete file. Shutting down.");
        running = false;
    }

    /**
     * Called when we receive a HAVE from a remote peer (new piece acquired by them).
     */
    public static void recordHaveFromPeer(int remotePeerId) {
        peerPieceCount.merge(remotePeerId, 1, Integer::sum);
        checkAllDone();
    }

    private static void shutdownNetworking() {
        if (listenSocket != null && !listenSocket.isClosed()) {
            try {
                listenSocket.close();
            } catch (IOException ignored) {}
        }
        for (PeerConnectionThread pct : connections) {
            pct.closeSocket();
        }
        for (PeerConnectionThread pct : connections) {
            try {
                pct.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}