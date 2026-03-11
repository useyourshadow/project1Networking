import java.nio.ByteBuffer;

public class Handshake {
    public static final String HEADER = "P2PFILESHARINGPROJ";
    public static final int SIZE = 32;

    private int peerId;

    public Handshake(int peerId) {
        this.peerId = peerId;
    }

    public int getPeerId() {
        return peerId;
    }

    public byte[] toByteArray() {
        byte[] bytes = new byte[SIZE];
        byte[] headerBytes = HEADER.getBytes();
        System.arraycopy(headerBytes, 0, bytes, 0, 18);
        ByteBuffer.wrap(bytes, 28, 4).putInt(peerId);
        return bytes;
    }

    public static Handshake fromByteArray(byte[] data) {
        if (data.length != SIZE) {
            throw new RuntimeException("Invalid handshake size: " + data.length);
        }
        String header = new String(data, 0, 18);
        if (!header.equals(HEADER)) {
            throw new RuntimeException("Invalid handshake header: " + header);
        }
        int peerId = ByteBuffer.wrap(data, 28, 4).getInt();
        return new Handshake(peerId);
    }
}
