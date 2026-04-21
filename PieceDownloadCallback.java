@FunctionalInterface
public interface PieceDownloadCallback {
    void onPieceDownloaded(int pieceIndex, boolean myFileComplete);
}
