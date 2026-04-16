import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class P2PLogger {
    private int peerId;
    private PrintWriter writer;
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public P2PLogger(int peerId) {
        this.peerId = peerId;
        try {
            writer = new PrintWriter(
                new FileWriter("log_peer_" + peerId + ".log", false));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String timestamp() {
        return LocalDateTime.now().format(FORMATTER);
    }

    private synchronized void log(String msg) {
        writer.println(msg);
        writer.flush();
        System.out.println(msg);
    }

    public void logMadeConnection(int remotePeerId) {
        log(timestamp() + ": Peer " + peerId +
            " makes a connection to Peer " + remotePeerId + ".");
    }

    public void logConnectedFrom(int remotePeerId) {
        log(timestamp() + ": Peer " + peerId +
            " is connected from Peer " + remotePeerId + ".");
    }

    public void logPreferredNeighbors(java.util.List<Integer> neighborIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < neighborIds.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(neighborIds.get(i));
        }
        log(timestamp() + ": Peer " + peerId +
            " has the preferred neighbors " + sb + ".");
    }

    public void logOptimisticallyUnchokedNeighbor(int neighborId) {
        log(timestamp() + ": Peer " + peerId +
            " has the optimistically unchoked neighbor " + neighborId + ".");
    }

    public void logUnchokedBy(int remotePeerId) {
        log(timestamp() + ": Peer " + peerId +
            " is unchoked by " + remotePeerId + ".");
    }

    public void logChokedBy(int remotePeerId) {
        log(timestamp() + ": Peer " + peerId +
            " is choked by " + remotePeerId + ".");
    }

    public void logReceivedHave(int remotePeerId, int pieceIndex) {
        log(timestamp() + ": Peer " + peerId +
            " received the 'have' message from " + remotePeerId +
            " for the piece " + pieceIndex + ".");
    }

    public void logReceivedInterested(int remotePeerId) {
        log(timestamp() + ": Peer " + peerId +
            " received the 'interested' message from " + remotePeerId + ".");
    }

    public void logReceivedNotInterested(int remotePeerId) {
        log(timestamp() + ": Peer " + peerId +
            " received the 'not interested' message from " + remotePeerId + ".");
    }

    public void logDownloadedPiece(int remotePeerId, int pieceIndex, int totalPieces) {
        log(timestamp() + ": Peer " + peerId +
            " has downloaded the piece " + pieceIndex + " from " + remotePeerId +
            ". Now the number of pieces it has is " + totalPieces + ".");
    }

    public void logDownloadComplete() {
        log(timestamp() + ": Peer " + peerId +
            " has downloaded the complete file.");
    }

    public void close() {
        writer.close();
    }
}
