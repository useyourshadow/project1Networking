import java.util.*;

public class ConnectionHandler {

    private Bitfield myBitfield;
    private Bitfield neighborBitfield;

    private int peerId;

    private Set<Integer> interestedPeers = new HashSet<>();

    public ConnectionHandler(int peerId, Bitfield myBitfield) {
        this.peerId = peerId;
        this.myBitfield = myBitfield;
    }


    public void handleBitfield(Message msg) {

        neighborBitfield = new Bitfield(myBitfield.getPieces().length);
        neighborBitfield.fromByteArray(msg.getPayload());

        if (myBitfield.isInterested(neighborBitfield)) {
            sendMessage(new Message(MessageType.INTERESTED));
        } else {
            sendMessage(new Message(MessageType.NOT_INTERESTED));
        }
    }

    public void handleInterested(int remotePeerId) {
        interestedPeers.add(remotePeerId);
    }

    public void handleNotInterested(int remotePeerId) {
        interestedPeers.remove(remotePeerId);
    }

    public void handleHave(int pieceIndex) {

        neighborBitfield.setPiece(pieceIndex);

        if (!myBitfield.hasPiece(pieceIndex)) {
            sendMessage(new Message(MessageType.INTERESTED));
        }
    }

    public void handlePiece(int pieceIndex) {

        myBitfield.setPiece(pieceIndex);

        System.out.println("Received piece " + pieceIndex);

        sendMessage(new Message(MessageType.HAVE, intToBytes(pieceIndex)));
    }

    private byte[] intToBytes(int value) {
        return new byte[] {
            (byte)(value >> 24),
            (byte)(value >> 16),
            (byte)(value >> 8),
            (byte)value
        };
    }

    private void sendMessage(Message msg) {
        System.out.println("Sending message type: " + msg.getType());
    }
}