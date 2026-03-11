import java.io.*;

public class Message {

    private int type;
    private byte[] payload;

    public Message(int type) {
        this.type = type;
        this.payload = new byte[0];
    }

    public Message(int type, byte[] payload) {
        this.type = type;
        this.payload = payload;
    }

    public int getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void send(DataOutputStream out) throws IOException {
        int messageLength = 1 + payload.length;
        out.writeInt(messageLength);
        out.writeByte(type);
        if (payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }

    public static Message receive(DataInputStream in) throws IOException {
        int messageLength = in.readInt();
        int type = in.readByte() & 0xFF;
        byte[] payload = new byte[messageLength - 1];
        if (payload.length > 0) {
            in.readFully(payload);
        }
        return new Message(type, payload);
    }
}
