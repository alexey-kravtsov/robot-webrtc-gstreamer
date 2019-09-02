var rtcConfiguration = {
    iceServers: [
        {urls: "stun:stun.services.mozilla.com"},
        {urls: "stun:stun.l.google.com:19302"}
    ]
};

var stompClient = null;
var peerConnection = null;

connect();

function connect() {
    var socket = new SockJS('/websocket-endpoint');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/receive/robot', processMessage);
    });
}

function stop() {
    // console.log("Message time: " + Date.now());
    // send('PING', '');
    peerConnection.close();
}

function processMessage(data) {
    let m = JSON.parse(data.body);
    switch (m.type) {
        case "MEDIA": {
            createAnswer().catch(e => console.log(e));
            break;
        }
        case "ICE": {
            break;
        }
    }
}

async function createAnswer() {
    const localStream = await navigator.mediaDevices.getUserMedia({audio: false, video: true});

    localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

}

function send(type, message) {
    stompClient.send("/operator", {}, JSON.stringify({
        type: type,
        message: JSON.stringify(message)
    }));
}

function onIceCandidate(event) {
    // We have a candidate, send it to the remote party with the
    // same uuid
    if (event.candidate == null) {
        console.log("ICE Candidate was null, done");
        return;
    }

    send("ICE", event.candidate);
}