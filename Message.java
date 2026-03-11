public class Message {

    public int type;
    public byte[] payload;

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
}