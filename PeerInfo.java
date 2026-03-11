import java.io.*;
import java.util.*;

public class PeerInfo {

    public int peerId;
    public String host;
    public int port;
    public boolean hasFile;

    public PeerInfo(int peerId, String host, int port, boolean hasFile) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.hasFile = hasFile;
    }

    public static List<PeerInfo> loadPeerInfo(String filename) {

        List<PeerInfo> peers = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {

            String line;

            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(" ");

                int peerId = Integer.parseInt(parts[0]);
                String host = parts[1];
                int port = Integer.parseInt(parts[2]);
                boolean hasFile = parts[3].equals("1");

                peers.add(new PeerInfo(peerId, host, port, hasFile));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return peers;
    }

}