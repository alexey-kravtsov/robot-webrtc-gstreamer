package robot.webrtc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import java.io.IOException;
import java.util.function.Consumer;

public class RobotSessionHandler extends StompSessionHandlerAdapter {

    private final ObjectReader objectReader;
    private final ObjectWriter objectWriter;

    private Logger logger = LogManager.getLogger(RobotSessionHandler.class);
    private StompSession session;

    private Consumer<SdpMessage> messageProcessor;

    public RobotSessionHandler() {
        ObjectMapper mapper = new ObjectMapper();
        objectReader = mapper.readerFor(SdpMessage.class);
        objectWriter = mapper.writerFor(SdpMessage.class);
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        this.session = session;
        logger.info("New session established : " + session.getSessionId());
        session.subscribe("/receive/robot", this);
    }

    @Override
    public void handleException(
            StompSession session,
            StompCommand command,
            StompHeaders headers,
            byte[] payload,
            Throwable exception) {
        logger.error("Got an exception", exception);
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        SdpMessage msg;
        try {
            msg = objectReader.readValue((String) payload);
        } catch (IOException e) {
            logger.error(e);
            return;
        }

        if (messageProcessor != null) {
            messageProcessor.accept(msg);
        }

        logger.info("Robot received: " + msg);
    }

    public void sendFrame(SdpMessage message) {
        try {
            String response = objectWriter.writeValueAsString(message);
            session.send("/operator", response);
        } catch (JsonProcessingException e) {
            logger.error(e);
        }
    }

    public void setMessageProcessor(Consumer<SdpMessage> processor) {
        messageProcessor = processor;
    }
}
