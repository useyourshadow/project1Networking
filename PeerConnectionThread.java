import java.io.*;
import java.net.*;

public class PeerConnectionThread extends Thread {
    private final Socket socket;
    private final int myPeerId;
    private int remotePeerId;
    private final Bitfield myBitfield;
    private final P2PLogger logger;
    private final boolean isInitiator;
    private final int numPieces;
    private final FileManager fileManager;
    private final PieceDownloadCallback pieceCallback;

    private ConnectionHandler handler;

    private DataInputStream in;
    private DataOutputStream out;

    public PeerConnectionThread(Socket socket, int myPeerId, int remotePeerId,
                                Bitfield myBitfield, P2PLogger logger,
                                int numPieces, FileManager fileManager,
                                PieceDownloadCallback pieceCallback) {
        this.socket          = socket;
        this.myPeerId        = myPeerId;
        this.remotePeerId    = remotePeerId;
        this.myBitfield      = myBitfield;
        this.logger          = logger;
        this.isInitiator     = true;
        this.numPieces       = numPieces;
        this.fileManager     = fileManager;
        this.pieceCallback   = pieceCallback;
    }

    public PeerConnectionThread(Socket socket, int myPeerId,
                                Bitfield myBitfield, P2PLogger logger,
                                int numPieces, FileManager fileManager,
                                PieceDownloadCallback pieceCallback) {
        this.socket          = socket;
        this.myPeerId        = myPeerId;
        this.remotePeerId    = -1;
        this.myBitfield      = myBitfield;
        this.logger          = logger;
        this.isInitiator     = false;
        this.numPieces       = numPieces;
        this.fileManager     = fileManager;
        this.pieceCallback   = pieceCallback;
    }

    public int getRemotePeerId() { return remotePeerId; }

    public ConnectionHandler getHandler() { return handler; }

    /** Unblocks {@link Message#receive} so the peer thread can exit on shutdown. */
    public void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public void sendHave(byte[] havePayload) throws IOException {
        if (out != null) {
            new Message(MessageType.HAVE, havePayload).send(out);
        }
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            performHandshake();

            handler = new ConnectionHandler(myPeerId, remotePeerId,
                                            myBitfield, fileManager, logger);
            handler.setOutputStream(out);
            handler.setPieceDownloadCallback(pieceCallback);

            sendBitfieldIfNeeded();

            while (PeerProcess.isRunning()) {
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
            ": Handshake done with peer " + remotePeerId);
    }

    private void sendBitfieldIfNeeded() throws IOException {
        boolean hasAny = false;
        for (boolean p : myBitfield.getPieces()) {
            if (p) { hasAny = true; break; }
        }
        if (hasAny) {
            byte[] packed = Util.packBitfield(myBitfield.getPieces());
            new Message(MessageType.BITFIELD, packed).send(out);
            System.out.println("Peer " + myPeerId +
                ": Sent bitfield to peer " + remotePeerId);
        }
    }


    private void handleMessage(Message msg) throws IOException {
        switch (msg.getType()) {
            case MessageType.BITFIELD: {
                byte[] unpacked = Util.unpackBitfield(msg.getPayload(), numPieces);
                handler.handleBitfield(unpacked);
                break;
            }
            case MessageType.INTERESTED:
                handler.handleInterested();
                break;
            case MessageType.NOT_INTERESTED:
                handler.handleNotInterested();
                break;
            case MessageType.CHOKE:
                handler.handleChoke();
                break;
            case MessageType.UNCHOKE:
                handler.handleUnchoke();
                break;
            case MessageType.HAVE: {
                int pieceIndex = Util.bytesToInt(msg.getPayload());
                handler.handleHave(pieceIndex);
                // Track remote peer's progress for termination detection.
                PeerProcess.recordHaveFromPeer(remotePeerId);
                break;
            }
            case MessageType.REQUEST:
                handler.handleRequest(msg.getPayload());
                break;
            case MessageType.PIECE:
                handler.handlePiece(msg.getPayload());
                break;
            default:
                System.out.println("Peer " + myPeerId +
                    ": Unknown message type " + msg.getType());
        }
    }
}