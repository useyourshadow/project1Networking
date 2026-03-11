import java.util.Arrays;

public class Bitfield {

    private boolean[] pieces;

    public Bitfield(int numPieces) {
        pieces = new boolean[numPieces];
    }

    public boolean hasPiece(int index) {
        return pieces[index];
    }

    public void setPiece(int index) {
        pieces[index] = true;
    }

    public boolean isComplete() {
        for (boolean piece : pieces) {
            if (!piece) {
                return false;
            }
        }
        return true;
    }

    public boolean isInterested(Bitfield neighbor) {
        for (int i = 0; i < pieces.length; i++) {
            if (neighbor.hasPiece(i) && !this.hasPiece(i)) {
                return true;
            }
        }
        return false;
    }

    public boolean[] getPieces() {
        return pieces;
    }

    public byte[] toByteArray() {
        byte[] bytes = new byte[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            bytes[i] = (byte) (pieces[i] ? 1 : 0);
        }
        return bytes;
    }

    public void fromByteArray(byte[] data) {
        for (int i = 0; i < data.length && i < pieces.length; i++) {
            pieces[i] = data[i] == 1;
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(pieces);
    }
}