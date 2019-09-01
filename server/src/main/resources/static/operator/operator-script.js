var rtcConfiguration = {
    iceServers: [
        {urls: "stun:stun.services.mozilla.com"},
        {urls: "stun:stun.l.google.com:19302"}
    ]
};

var stompClient = null;
var peerConnection = null;

function connect() {
    var socket = new SockJS('/websocket-endpoint');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/receive/operator', processMessage);

        peerConnection = new RTCPeerConnection(rtcConfiguration);
        peerConnection.ontrack = onRemoteTrack;
        peerConnection.onicecandidate = onIceCandidate;

        send('START', '');
    });
}

function stop() {
    peerConnection.close();
    send('STOP', '');
}

function processMessage(data) {
    var m = JSON.parse(data.body);
    switch (m.type) {
        case "MEDIA": {
            var sdp = JSON.parse(m.message);
            var desc = new RTCSessionDescription(sdp);
            peerConnection.setRemoteDescription(desc)
                .then(function() {
                    return peerConnection.createAnswer();
                })
                .then(function(answer) {
                    return peerConnection.setLocalDescription(answer)
                })
                .then(function() {
                    send("MEDIA", peerConnection.localDescription);
                });
            break;
        }
        case "ICE": {
            var ice = JSON.parse(m.message);
            var candidate = new RTCIceCandidate(ice);
            peerConnection.addIceCandidate(candidate);
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