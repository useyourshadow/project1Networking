import java.io.*;
import java.util.*;

/**
  Layout on disk:
    peer_[id]/[fileName]   — the file being shared (may be partial)
 
  pieces are stored directly into the correct byte offset of that file so
  that when all pieces are present the file is complete with no extra work.
 */
public class FileManager {

    private final String filePath;   // e.g. "peer_1001/TheFile.dat"
    private final int pieceSize;
    private final int fileSize;
    private final int numPieces;
    private final RandomAccessFile raf;

    public FileManager(int peerId, String fileName,
                       int fileSize, int pieceSize, int numPieces) throws IOException {
        File dir = new File("peer_" + peerId);
        if (!dir.exists()) dir.mkdirs();

        this.filePath  = "peer_" + peerId + File.separator + fileName;
        this.pieceSize = pieceSize;
        this.fileSize  = fileSize;
        this.numPieces = numPieces;

        raf = new RandomAccessFile(filePath, "rw");
        if (raf.length() < fileSize) {
            raf.setLength(fileSize);
        }
    }

    public int getPieceLength(int pieceIndex) {
        if (pieceIndex == numPieces - 1) {
            int remainder = fileSize % pieceSize;
            return remainder == 0 ? pieceSize : remainder;
        }
        return pieceSize;
    }

    public synchronized byte[] readPiece(int pieceIndex) throws IOException {
        int len = getPieceLength(pieceIndex);
        byte[] data = new byte[len];
        raf.seek((long) pieceIndex * pieceSize);
        raf.readFully(data);
        return data;
    }

    
    public synchronized void writePiece(int pieceIndex, byte[] data) throws IOException {
        raf.seek((long) pieceIndex * pieceSize);
        raf.write(data);
    }

    public void close() throws IOException {
        raf.close();
    }

    public static Bitfield loadBitfieldFromDisk(int peerId, String fileName,
                                                int numPieces, int pieceSize,
                                                int fileSize) {
        Bitfield bf = new Bitfield(numPieces);
        File f = new File("peer_" + peerId + File.separator + fileName);
        if (!f.exists() || f.length() < fileSize) return bf;

        for (int i = 0; i < numPieces; i++) bf.setPiece(i);
        return bf;
    }
}