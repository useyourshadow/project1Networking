import java.io.*;
import java.util.*;

public class PeerInfo {
    public int peerId;
    public String hostname;
    public int port;
    public boolean hasFile;

    public PeerInfo(int peerId, String hostname, int port, boolean hasFile) {
        this.peerId = peerId;
        this.hostname = hostname;
        this.port = port;
        this.hasFile = hasFile;
    }

    public static List<PeerInfo> load(String path) throws IOException {
        List<PeerInfo> peers = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            int peerId = Integer.parseInt(parts[0]);
            String hostname = parts[1];
            int port = Integer.parseInt(parts[2]);
            boolean hasFile = parts[3].equals("1");
            peers.add(new PeerInfo(peerId, hostname, port, hasFile));
        }
        reader.close();
        return peers;
    }
}
