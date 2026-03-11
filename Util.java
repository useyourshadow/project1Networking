import java.nio.ByteBuffer;

public class Util {

    public static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    public static int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public static byte[] packBitfield(boolean[] pieces) {
        int numBytes = (int) Math.ceil(pieces.length / 8.0);
        byte[] packed = new byte[numBytes];
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i]) {
                packed[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        return packed;
    }

    public static byte[] unpackBitfield(byte[] packed, int numPieces) {
        byte[] unpacked = new byte[numPieces];
        for (int i = 0; i < numPieces; i++) {
            if ((packed[i / 8] & (1 << (7 - (i % 8)))) != 0) {
                unpacked[i] = 1;
            }
        }
        return unpacked;
    }
}
