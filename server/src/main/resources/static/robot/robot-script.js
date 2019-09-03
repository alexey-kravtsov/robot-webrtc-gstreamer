const rtcConfiguration = {
    iceServers: [
        {urls: "stun:stun.services.mozilla.com"},
        {urls: "stun:stun.l.google.com:19302"}
    ]
};

let stompClient = null;
let peerConnection = null;

connect();

function connect() {
    const socket = new SockJS('/websocket-endpoint');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/receive/robot', processMessage);
    });
}

function stop() {
    peerConnection.close();
}

async function processMessage(data) {
    let m = JSON.parse(data.body);
    switch (m.type) {
        case "MEDIA": {
            const offer = JSON.parse(m.message);
            const desc = new RTCSessionDescription(offer);

            peerConnection = new RTCPeerConnection(rtcConfiguration);
            peerConnection.addEventListener('icecandidate', onIceCandidate);

            const localStream = await navigator.mediaDevices.getUserMedia({audio: false, video: true});

            localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

            await peerConnection.setRemoteDescription(desc);
            const answer = await peerConnection.createAnswer();
            await peerConnection.setLocalDescription(answer);

            send('MEDIA', answer);

            break;
        }
        case "ICE": {
            const ice = JSON.parse(m.message);
            const candidate = new RTCIceCandidate(ice);
            await peerConnection.addIceCandidate(candidate);
            break;
        }
        case "STOP": {
            peerConnection.close();
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