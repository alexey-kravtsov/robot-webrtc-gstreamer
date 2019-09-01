package robot;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import robot.webrtc.RobotSessionHandler;
import robot.webrtc.RobotVideoService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class RobotApplication {

    private static String URL = "ws://192.168.0.104:8080/websocket-endpoint";

    private static RobotVideoService videoService;

    public static void main(String[] args) throws IOException {
        List<Transport> transports = Collections.singletonList(
                new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketClient transport = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(transport);
        stompClient.setMessageConverter(new StringMessageConverter());

        RobotSessionHandler sessionHandler = new RobotSessionHandler();
        videoService = new RobotVideoService(sessionHandler);
        stompClient.connect(URL, sessionHandler);

        videoService.start();

        System.in.read();
    }
}
