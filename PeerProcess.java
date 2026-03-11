import java.io.*;
import java.net.*;
import java.util.*;

public class PeerProcess {

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
            System.out.println("Config loaded: " + config.numPieces + " pieces, "
                + "fileSize=" + config.fileSize + ", pieceSize=" + config.pieceSize);

            PeerInfo myInfo = null;
            List<PeerInfo> earlierPeers = new ArrayList<>();
            for (PeerInfo pi : allPeers) {
                if (pi.peerId == myPeerId) {
                    myInfo = pi;
                    break;
                }
                earlierPeers.add(pi);
            }

            if (myInfo == null) {
                System.out.println("Error: Peer ID " + myPeerId
                    + " not found in PeerInfo.cfg");
                return;
            }

            final PeerInfo myPeerInfo = myInfo;

            Bitfield myBitfield = new Bitfield(config.numPieces);
            if (myInfo.hasFile) {
                for (int i = 0; i < config.numPieces; i++) {
                    myBitfield.setPiece(i);
                }
                System.out.println("Peer " + myPeerId + " has the complete file.");
            } else {
                System.out.println("Peer " + myPeerId + " does not have the file.");
            }

            File peerDir = new File("peer_" + myPeerId);
            if (!peerDir.exists()) {
                peerDir.mkdirs();
            }

            P2PLogger logger = new P2PLogger(myPeerId);

            Thread serverThread = new Thread(() -> {
                try {
                    ServerSocket serverSocket = new ServerSocket(myPeerInfo.port);
                    System.out.println("Peer " + myPeerId
                        + " listening on port " + myPeerInfo.port);
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        PeerConnectionThread pct = new PeerConnectionThread(
                            clientSocket, myPeerId, myBitfield, logger,
                            config.numPieces);
                        pct.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
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
                        System.out.println("Peer " + myPeerId
                            + " connecting to peer " + pi.peerId
                            + " at " + pi.hostname + ":" + pi.port + "...");
                        Socket socket = new Socket(pi.hostname, pi.port);
                        PeerConnectionThread pct = new PeerConnectionThread(
                            socket, myPeerId, pi.peerId, myBitfield, logger,
                            config.numPieces);
                        pct.start();
                        connected = true;
                    } catch (ConnectException e) {
                        retries--;
                        System.out.println("  Peer " + pi.peerId
                            + " not ready, retrying in 2s... ("
                            + retries + " left)");
                        Thread.sleep(2000);
                    }
                }
                if (!connected) {
                    System.out.println("  Failed to connect to peer " + pi.peerId);
                }
            }

            System.out.println("Peer " + myPeerId + " startup complete.");

            while (true) {
                Thread.sleep(10000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
