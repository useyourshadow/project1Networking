import java.io.*;

public class CommonConfig {
    public int numberOfPreferredNeighbors;
    public int unchokingInterval;
    public int optimisticUnchokingInterval;
    public String fileName;
    public int fileSize;
    public int pieceSize;
    public int numPieces;

    public static CommonConfig load(String path) throws IOException {
        CommonConfig config = new CommonConfig();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) continue;
            switch (parts[0]) {
                case "NumberOfPreferredNeighbors":
                    config.numberOfPreferredNeighbors = Integer.parseInt(parts[1]);
                    break;
                case "UnchokingInterval":
                    config.unchokingInterval = Integer.parseInt(parts[1]);
                    break;
                case "OptimisticUnchokingInterval":
                    config.optimisticUnchokingInterval = Integer.parseInt(parts[1]);
                    break;
                case "FileName":
                    config.fileName = parts[1];
                    break;
                case "FileSize":
                    config.fileSize = Integer.parseInt(parts[1]);
                    break;
                case "PieceSize":
                    config.pieceSize = Integer.parseInt(parts[1]);
                    break;
            }
        }
        reader.close();
        config.numPieces = (int) Math.ceil((double) config.fileSize / config.pieceSize);
        return config;
    }
}
