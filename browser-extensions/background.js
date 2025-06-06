chrome.downloads.onCreated.addListener(async (downloadItem) => {
  chrome.downloads.pause(downloadItem.id);
  await chrome.storage.local.set({ pendingDownload: downloadItem });
  chrome.action.openPopup();
});

const COMMANDS = {
  SERVER_HELLO: 1
};

class WebSocketClient {
  constructor(url) {
    this.messageId = 0;
    this.ports = [];

    this.socket = new WebSocket(url);
    this.socket.binaryType = "arraybuffer";
    this.socket.onmessage = (event) => this._onSocketMessage(event);
    this.socket.onerror = (event) => this._onSocketError(event);
    this.socket.onclose = () => this._onSocketClose();
  }

  _onSocketMessage(event) {
    if (event.data instanceof ArrayBuffer) {
      const parsed = parseBinaryMessage(event.data);
      if (parsed.id === 0) {
        this._handleServerMessage(parsed);
      } else {
        this._handleServerResponse(parsed);
      }
    } else {
      console.log("Received unknown message, ignoring it.", event)
    }
  }

  _onSocketError(event) {
    console.log("WebSocket error: ", event)
    this.socket.close();
  }

  _onSocketClose() {
    // TODO impl
  }

  _handleServerMessage({ command, body }) {
    switch (command) {
      case COMMANDS.SERVER_HELLO:
        this.serverHello = JSON.parse(body);
        this._broadcastMessages({ command: 'server_hello_response', serverHello: this.serverHello });
        break;
    }
  }

  _handleServerResponse(message) {
    this._broadcastMessages({ command: 'message', message });
  }

  _broadcastMessages(message) {
    for (let port of this.ports) {
      port.postMessage({ type: 'ws', ...message });
    }
  }

  send({ command, body }) {
    const messageId = ++this.messageId;
    const binary = buildBinaryMessage({ messageId, command, body });
    this.socket.send(binary);
    return messageId;
  }

  isOpen() {
    return this.socket.readyState == WebSocket.OPEN;
  }

  addPort(port) {
    this.ports.push(port);
  }
}

function buildBinaryMessage({ messageId, command, body }) {
  const encoder = new TextEncoder();
  const textBytes = encoder.encode(body);
  const buffer = new ArrayBuffer(6 + textBytes.length);
  const view = new DataView(buffer);

  view.setUint32(0, messageId, false); // big-endian
  view.setUint16(4, command, false);   // big-endian

  new Uint8Array(buffer, 6).set(textBytes);
  return buffer;
}

function parseBinaryMessage(buffer) {
  const view = new DataView(buffer);
  const decoder = new TextDecoder("utf-8");

  const id = view.getUint32(0, false);
  const command = view.getUint16(4, false);

  const bodyBytes = new Uint8Array(buffer, 6);
  const body = decoder.decode(bodyBytes);

  return { id, command, body };
}

let webSocketClient = null;
function ensureWebSocketClient() {
  if (webSocketClient && webSocketClient.isOpen()) {
    return webSocketClient;
  }

  webSocketClient = new WebSocketClient("ws://127.0.0.1:8080/websocket")
  return webSocketClient;
}

chrome.runtime.onConnect.addListener((port) => {
  if (port.name === "popup") {
    ensureWebSocketClient().addPort(port);

    port.onMessage.addListener((msg) => {
      if (msg.type === 'ws') {
        switch (msg.command) {
          case 'send':
            const id = ensureWebSocketClient().send(msg.message);
            port.postMessage({ type: 'ws', command: 'send_response', id, clientId: msg.id });
            break;
          
          case 'server_hello':
            const serverHello = ensureWebSocketClient().serverHello;
            if (serverHello) {
              port.postMessage({ type: 'ws', command: 'server_hello_response', serverHello });
            }
            break;
        }
      }
    });
  }
});