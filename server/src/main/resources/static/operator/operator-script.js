const rtcConfiguration = {
    iceServers: [
        {urls: "stun:stun.services.mozilla.com"},
        {urls: "stun:stun.l.google.com:19302"}
    ]
};

let stompClient = null;
let peerConnection = null;

function connect() {
    let socket = new SockJS('/websocket-endpoint');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, async function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/receive/operator', processMessage);

        peerConnection = new RTCPeerConnection(rtcConfiguration);
        peerConnection.addEventListener('icecandidate', onIceCandidate);
        peerConnection.addEventListener('track', onRemoteTrack);
    });
}

function stop() {
    peerConnection.close();
}

function processMessage(data) {
    let m = JSON.parse(data.body);
    switch (m.type) {
        case "MEDIA": {
            break;
        }
        case "ICE": {
            break;
        }
    }
}

function send(type, message) {
    stompClient.send("/robot", {}, JSON.stringify({
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

function onRemoteTrack(event) {
    var videoElement = getVideoElement();
    var stream = event.streams[0];
    console.log("stream: " + stream);
    if (videoElement.srcObject !== stream) {
        console.log('Incoming stream');
        videoElement.srcObject = stream;
    }
}

function getVideoElement() {
    return document.getElementById("stream");
}