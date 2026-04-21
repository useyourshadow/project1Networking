import java.io.*;
import java.util.*;

/**
 * part A (choking/unchoking) will call:
 *   setChoked(boolean)       — to choke or unchoke this neighbor
 *   isRemoteInterested()     — to know if this neighbor wants our data
 *   getDownloadRate()        — to rank neighbors for preferred-neighbor selection
 *   resetDownloadRate()      — called every unchoking interval by Part A
 *
 */
public class ConnectionHandler {


    private volatile boolean remoteIsInterested = false;

    private volatile boolean amChokedByRemote = true;

    private volatile boolean amChokingRemote = true;

    private volatile long bytesDownloadedThisInterval = 0;

    private final int myPeerId;
    private final int remotePeerId;
    private final Bitfield myBitfield;
    private Bitfield neighborBitfield;

    private final FileManager fileManager;
    private final P2PLogger logger;
    private int pendingRequest = -1;
    private DataOutputStream out;
    private PieceDownloadCallback pieceCallback;

    public ConnectionHandler(int myPeerId, int remotePeerId,
                             Bitfield myBitfield, FileManager fileManager,
                             P2PLogger logger) {
        this.myPeerId     = myPeerId;
        this.remotePeerId = remotePeerId;
        this.myBitfield   = myBitfield;
        this.fileManager  = fileManager;
        this.logger       = logger;
    }

    public void setOutputStream(DataOutputStream out) {
        this.out = out;
    }

    public void setPieceDownloadCallback(PieceDownloadCallback cb) {
        this.pieceCallback = cb;
    }

    public synchronized void handleBitfield(byte[] unpackedData) throws IOException {
        neighborBitfield = new Bitfield(myBitfield.getPieces().length);
        neighborBitfield.fromByteArray(unpackedData);
        PeerProcess.recordNeighborPieceCount(remotePeerId, neighborBitfield.countTruePieces());
        sendInterestDecision();
    }

    public synchronized void handleHave(int pieceIndex) throws IOException {
        if (neighborBitfield == null) {
            neighborBitfield = new Bitfield(myBitfield.getPieces().length);
        }
        neighborBitfield.setPiece(pieceIndex);
        logger.logReceivedHave(remotePeerId, pieceIndex);
        sendInterestDecision();
    }

    public synchronized void handleInterested() {
        remoteIsInterested = true;
        logger.logReceivedInterested(remotePeerId);
    }

    public synchronized void handleNotInterested() {
        remoteIsInterested = false;
        logger.logReceivedNotInterested(remotePeerId);
    }

    public synchronized void handleUnchoke() throws IOException {
        amChokedByRemote = false;
        logger.logUnchokedBy(remotePeerId);
        sendNextRequest();
    }

    public synchronized void handleChoke() {
        amChokedByRemote = true;
        if (pendingRequest >= 0) {
            PeerProcess.releaseInflightRequest(pendingRequest);
            pendingRequest = -1;
        }
        logger.logChokedBy(remotePeerId);
    }

    public synchronized void handlePiece(byte[] payload) throws IOException {
        if (payload.length < 4) return;

        int pieceIndex = Util.bytesToInt(Arrays.copyOfRange(payload, 0, 4));
        byte[] pieceData = Arrays.copyOfRange(payload, 4, payload.length);

        if (myBitfield.hasPiece(pieceIndex)) {
            if (pendingRequest == pieceIndex) {
                PeerProcess.releaseInflightRequest(pieceIndex);
                pendingRequest = -1;
            }
            return;
        }

        fileManager.writePiece(pieceIndex, pieceData);
        myBitfield.setPiece(pieceIndex);
        bytesDownloadedThisInterval += pieceData.length;
        PeerProcess.releaseInflightRequest(pieceIndex);
        pendingRequest = -1;

        int totalOwned = countOwnedPieces();
        logger.logDownloadedPiece(remotePeerId, pieceIndex, totalOwned);

        // Notify PeerProcess: broadcast HAVE + check termination.
        if (pieceCallback != null) {
            pieceCallback.onPieceDownloaded(pieceIndex, myBitfield.isComplete());
        }

        // Request next piece if still unchoked.
        if (!amChokedByRemote) {
            sendNextRequest();
        }

        sendInterestDecision();
    }

    /*
     remote peer requested a piece from us. serve it only if we are not currently choking them, payload: 4-byte piece index.
     */
    public synchronized void handleRequest(byte[] payload) throws IOException {
        if (payload.length < 4) return;
        int pieceIndex = Util.bytesToInt(payload);

        // if were choking them, silentl ignore 
        if (amChokingRemote) return;
        if (!myBitfield.hasPiece(pieceIndex)) return;

        byte[] pieceData = fileManager.readPiece(pieceIndex);

        // PIECE payload: [4-byte index][data]
        byte[] piecePayload = new byte[4 + pieceData.length];
        System.arraycopy(Util.intToBytes(pieceIndex), 0, piecePayload, 0, 4);
        System.arraycopy(pieceData, 0, piecePayload, 4, pieceData.length);

        new Message(MessageType.PIECE, piecePayload).send(out);
    }

    // called by Part A (choking/unchoking engine)

    
    public synchronized void setChoked(boolean choked) throws IOException {
        if (choked == amChokingRemote) return;
        amChokingRemote = choked;
        if (choked) {
            new Message(MessageType.CHOKE).send(out);
        } else {
            new Message(MessageType.UNCHOKE).send(out);
        }
    }

    public boolean isRemoteInterested()  { return remoteIsInterested; }
    public boolean isChokingRemote()     { return amChokingRemote; }
    public boolean isChokedByRemote()    { return amChokedByRemote; }
    public long    getDownloadRate()     { return bytesDownloadedThisInterval; }
    public int     getRemotePeerId()     { return remotePeerId; }

    public Bitfield getNeighborBitfield() { return neighborBitfield; }

    public synchronized void resetDownloadRate() {
        bytesDownloadedThisInterval = 0;
    }

 
    private void sendInterestDecision() throws IOException {
        if (neighborBitfield == null) return;
        if (myBitfield.isInterested(neighborBitfield)) {
            new Message(MessageType.INTERESTED).send(out);
        } else {
            new Message(MessageType.NOT_INTERESTED).send(out);
        }
    }

   
    private void sendNextRequest() throws IOException {
        if (amChokedByRemote || neighborBitfield == null) return;

        List<Integer> candidates = new ArrayList<>();
        boolean[] mine = myBitfield.getPieces();
        for (int i = 0; i < mine.length; i++) {
            if (!mine[i] && neighborBitfield.hasPiece(i)
                && !PeerProcess.isInflightRequestRegistered(i)) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) return;

        Collections.shuffle(candidates);
        for (int chosen : candidates) {
            if (PeerProcess.tryRegisterInflightRequest(chosen)) {
                pendingRequest = chosen;
                new Message(MessageType.REQUEST, Util.intToBytes(chosen)).send(out);
                return;
            }
        }
    }

    private int countOwnedPieces() {
        int count = 0;
        for (boolean p : myBitfield.getPieces()) if (p) count++;
        return count;
    }
}