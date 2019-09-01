package robot.webrtc;

public class SdpMessage {
    private SdpMessageType type;
    private String message;

    public SdpMessageType getType() {
        return type;
    }

    public void setType(SdpMessageType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("type=%s,message=%s", type, message);
    }
}
