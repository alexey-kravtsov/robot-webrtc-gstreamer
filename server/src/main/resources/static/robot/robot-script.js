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
    send('STOP', '');
}

var mediaConstraints = {
    offerToReceiveAudio: true,
    offerToReceiveVideo: true
};

function processMessage(data) {
    var m = JSON.parse(data.body);
    switch (m.type) {
        case "START": {
            peerConnection = new RTCPeerConnection(rtcConfiguration);
            peerConnection.onicecandidate = onIceCandidate;
            peerConnection.createOffer(mediaConstraints)
                .then(function(offer) {
                    return peerConnection.setLocalDescription(offer);
                })
                .then(function() {
                    send("MEDIA", peerConnection.localDescription)
                });
            break;
        }
        case "MEDIA": {
            var sdp = JSON.parse(m.message);
            var desc = new RTCSessionDescription(sdp);
            peerConnection.setRemoteDescription(desc)
                .then(function () {
                    return navigator.mediaDevices.getUserMedia({video: true, audio: false});
                })
                .then(function(stream) {
                    var localStream = stream;
                    localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));
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

function onSdpError(e) {
    console.error('onSdpError', e);
}