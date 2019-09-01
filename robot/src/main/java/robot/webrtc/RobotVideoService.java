package robot.webrtc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.webrtc.WebRTCBin;
import org.freedesktop.gstreamer.webrtc.WebRTCSDPType;
import org.freedesktop.gstreamer.webrtc.WebRTCSessionDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RobotVideoService {
    private static final String VIDEO_BIN_DESCRIPTION = "v4l2src device=\"/dev/video0\" ! videoconvert ! video/x-raw,width=640,height=480,format=(string)YV12 ! x264enc speed-preset=ultrafast tune=zerolatency byte-stream=true threads=4 key-int-max=15 intra-refresh=true ! rtph264pay pt=96 ! capsfilter caps=application/x-rtp,media=video,encoding-name=H264,payload=96";
    //private static final String VIDEO_BIN_DESCRIPTION = "videotestsrc ! videoconvert ! queue ! vp8enc deadline=1 ! rtpvp8pay ! queue ! capsfilter caps=application/x-rtp,media=video,encoding-name=VP8,payload=97";

    private final Logger logger = LoggerFactory.getLogger(RobotVideoService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private RobotSessionHandler sessionHandler;
    private WebRTCBin webRTCBin;
    private Pipeline pipe;

    public RobotVideoService(RobotSessionHandler sessionHandler) {
        this.sessionHandler = sessionHandler;
        this.sessionHandler.setMessageProcessor(this::processMessage);

        Gst.init(new Version(1, 14));
    }

    public void start() {
        Gst.main();
    }

    public void processMessage(SdpMessage message) {
        JsonNode jsonMessage;
        try {
            jsonMessage = mapper.readTree(message.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        switch (message.getType()) {
            case PING: {
                logger.info("Message time: " + System.currentTimeMillis());
                break;
            }
            case START: {
                webRTCBin = new WebRTCBin("sendrecv");

                Bin videoBin = Gst.parseBinFromDescription(VIDEO_BIN_DESCRIPTION, true);

                pipe = new Pipeline();
                pipe.addMany(webRTCBin, videoBin);
                videoBin.link(webRTCBin);
                setupPipeLogging(pipe);

                // When the pipeline goes to PLAYING, the on_negotiation_needed() callback will be called, and we will ask webrtcbin to create an offer which will match the pipeline above.
                webRTCBin.connect(onNegotiationNeeded);
                webRTCBin.connect(onIceCandidate);

                pipe.play();
                break;
            }
            case STOP: {
                pipe.stop();
                pipe.dispose();

                webRTCBin.stop();
                webRTCBin.dispose();

                Gst.quit();
                break;
            }
            case MEDIA: {
                SDPMessage sdpMessage = new SDPMessage();
                String sdpPayload = jsonMessage.get("sdp").textValue();
                sdpMessage.parseBuffer(sdpPayload);
                WebRTCSessionDescription description = new WebRTCSessionDescription(WebRTCSDPType.ANSWER, sdpMessage);
                webRTCBin.setRemoteDescription(description);
                break;
            }
            case ICE: {
                String candidate = jsonMessage.get("candidate").textValue();
                int sdpMLineIndex = jsonMessage.get("sdpMLineIndex").intValue();
                webRTCBin.addIceCandidate(sdpMLineIndex, candidate);
                break;
            }
        }
    }

    private WebRTCBin.CREATE_OFFER onOfferCreated = offer -> {
        logger.info("Offer created");
        webRTCBin.setLocalDescription(offer);

        try {
            ObjectNode sdpNode = mapper.createObjectNode();
            sdpNode.put("type", "offer");
            sdpNode.put("sdp", offer.getSDPMessage().toString());
            String json = mapper.writeValueAsString(sdpNode);

            SdpMessage message = new SdpMessage();
            message.setType(SdpMessageType.MEDIA);
            message.setMessage(json);
            sessionHandler.sendFrame(message);
        } catch (JsonProcessingException e) {
            logger.error("Couldn't write JSON", e);
        }
    };

    private WebRTCBin.ON_NEGOTIATION_NEEDED onNegotiationNeeded = elem -> {
        logger.info("onNegotiationNeeded: " + elem.getName());

        // When webrtcbin has created the offer, it will hit our callback and we send SDP offer over the websocket to signalling server
        webRTCBin.createOffer(onOfferCreated);
    };

    private WebRTCBin.ON_ICE_CANDIDATE onIceCandidate = (sdpMLineIndex, candidate) -> {
        ObjectNode iceNode = mapper.createObjectNode();
        iceNode.put("candidate", candidate);
        iceNode.put("sdpMLineIndex", sdpMLineIndex);

        try {
            SdpMessage message = new SdpMessage();
            message.setType(SdpMessageType.ICE);

            String json = mapper.writeValueAsString(iceNode);
            message.setMessage(json);
            sessionHandler.sendFrame(message);
        } catch (JsonProcessingException e) {
            logger.error("Couldn't write JSON", e);
        }
    };

    private void setupPipeLogging(Pipeline pipe) {
        Bus bus = pipe.getBus();
        bus.connect((Bus.EOS) source -> {
            logger.info("Reached end of stream: " + source.toString());
            Gst.quit();
        });

        bus.connect((Bus.ERROR) (source, code, message) -> {
            logger.error("Error from source: '{}', with code: {}, and message '{}'", source, code, message);
        });

        bus.connect((source, old, current, pending) -> {
            if (source instanceof Pipeline) {
                logger.info("Pipe state changed from {} to new {}", old, current);
            }
        });
    }
}
