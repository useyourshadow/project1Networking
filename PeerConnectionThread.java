import java.io.*;
import java.net.*;

public class PeerConnectionThread extends Thread {
    private Socket socket;
    private int myPeerId;
    private int remotePeerId;
    private Bitfield myBitfield;
    private ConnectionHandler handler;
    private DataInputStream in;
    private DataOutputStream out;
    private P2PLogger logger;
    private boolean isInitiator;
    private int numPieces;

    /** Outgoing connection (we know the remote peer ID). */
    public PeerConnectionThread(Socket socket, int myPeerId, int remotePeerId,
                                Bitfield myBitfield, P2PLogger logger, int numPieces) {
        this.socket = socket;
        this.myPeerId = myPeerId;
        this.remotePeerId = remotePeerId;
        this.myBitfield = myBitfield;
        this.logger = logger;
        this.isInitiator = true;
        this.numPieces = numPieces;
    }

    /** Incoming connection (remote peer ID is unknown until handshake). */
    public PeerConnectionThread(Socket socket, int myPeerId,
                                Bitfield myBitfield, P2PLogger logger, int numPieces) {
        this.socket = socket;
        this.myPeerId = myPeerId;
        this.remotePeerId = -1;
        this.myBitfield = myBitfield;
        this.logger = logger;
        this.isInitiator = false;
        this.numPieces = numPieces;
    }

    public int getRemotePeerId() {
        return remotePeerId;
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            performHandshake();

            handler = new ConnectionHandler(myPeerId, myBitfield);

            sendBitfieldIfNeeded();

            while (true) {
                Message msg = Message.receive(in);
                handleMessage(msg);
            }
        } catch (EOFException e) {
            System.out.println("Peer " + myPeerId +
                ": Connection with peer " + remotePeerId + " closed.");
        } catch (IOException e) {
            System.out.println("Peer " + myPeerId +
                ": Error with peer " + remotePeerId + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void performHandshake() throws IOException {
        Handshake hs = new Handshake(myPeerId);
        out.write(hs.toByteArray());
        out.flush();

        byte[] hsData = new byte[Handshake.SIZE];
        in.readFully(hsData);
        Handshake received = Handshake.fromByteArray(hsData);

        if (isInitiator) {
            if (received.getPeerId() != remotePeerId) {
                throw new IOException("Handshake peer ID mismatch: expected "
                    + remotePeerId + ", got " + received.getPeerId());
            }
            logger.logMadeConnection(remotePeerId);
        } else {
            remotePeerId = received.getPeerId();
            logger.logConnectedFrom(remotePeerId);
        }

        System.out.println("Peer " + myPeerId +
            ": Handshake completed with peer " + remotePeerId);
    }

    private void sendBitfieldIfNeeded() throws IOException {
        boolean hasAnyPiece = false;
        for (boolean p : myBitfield.getPieces()) {
            if (p) { hasAnyPiece = true; break; }
        }

        if (hasAnyPiece) {
            byte[] packed = Util.packBitfield(myBitfield.getPieces());
            new Message(MessageType.BITFIELD, packed).send(out);
            System.out.println("Peer " + myPeerId +
                ": Sent bitfield to peer " + remotePeerId);
        }
    }

    private void handleMessage(Message msg) throws IOException {
        switch (msg.getType()) {
            case MessageType.BITFIELD:
                handleBitfieldMessage(msg);
                break;
            case MessageType.INTERESTED:
                handler.handleInterested(remotePeerId);
                logger.logReceivedInterested(remotePeerId);
                break;
            case MessageType.NOT_INTERESTED:
                handler.handleNotInterested(remotePeerId);
                logger.logReceivedNotInterested(remotePeerId);
                break;
            case MessageType.CHOKE:
                logger.logChokedBy(remotePeerId);
                break;
            case MessageType.UNCHOKE:
                logger.logUnchokedBy(remotePeerId);
                break;
            case MessageType.HAVE:
                int pieceIndex = Util.bytesToInt(msg.getPayload());
                handler.handleHave(pieceIndex);
                logger.logReceivedHave(remotePeerId, pieceIndex);
                break;
            default:
                System.out.println("Peer " + myPeerId +
                    ": Unknown message type " + msg.getType());
                break;
        }
    }

    private void handleBitfieldMessage(Message msg) throws IOException {
        byte[] unpacked = Util.unpackBitfield(msg.getPayload(), numPieces);

        handler.handleBitfield(new Message(MessageType.BITFIELD, unpacked));

        Bitfield neighborBf = new Bitfield(numPieces);
        neighborBf.fromByteArray(unpacked);
        if (myBitfield.isInterested(neighborBf)) {
            new Message(MessageType.INTERESTED).send(out);
            System.out.println("Peer " + myPeerId +
                ": Sent INTERESTED to peer " + remotePeerId);
        } else {
            new Message(MessageType.NOT_INTERESTED).send(out);
            System.out.println("Peer " + myPeerId +
                ": Sent NOT_INTERESTED to peer " + remotePeerId);
        }
    }
}
