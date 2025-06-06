const COMMANDS = {
  ERROR: 2,
  DOWNLOAD_URL: 4
};

class WebSocketClient {
  constructor(port) {
    this._port = port;
    this._messageId = 0;
    this._messageHandlers = {};

    port.onMessage.addListener((msg) => this._handlePortMessage(msg));
  }

  _handlePortMessage(msg) {
    if (msg.type === 'ws') {
      if (msg.command === 'message') {
        const message = msg.message;
        const handler = this._messageHandlers[message.id];
        if (handler) {
          const body = JSON.parse(message.body);
          if (message.command === COMMANDS.ERROR) {
            handler.reject({ command: message.command, body })
          } else {
            handler.resolve({ command: message.command, body });
          }
          delete this._messageHandlers[message.id];
        }
      } else if (msg.command === "send_response") {
        const { clientId, id } = msg;
        const promise = this._messageHandlers[clientId];
        this._messageHandlers[id] = promise;
        delete this._messageHandlers[clientId];
      }
    }
  }

  _send(command, body) {
    const id = -(++this._messageId);

    let promiseResolve;
    let promiseReject;
    const promise = new Promise((resolve, reject) => {
      promiseResolve = resolve;
      promiseReject = reject;
    });

    this._messageHandlers[id] = { resolve: promiseResolve, reject: promiseReject };

    this._port.postMessage({
      type: 'ws',
      command: 'send',
      message: { command, body },
      id
    });

    return promise;
  }

  downloadFile(url, fileName) {
    return this._send(COMMANDS.DOWNLOAD_URL, JSON.stringify({ url, fileName }));
  }
}

const port = chrome.runtime.connect({ name: "popup" });
const webSocketClient = new WebSocketClient(port);

document.getElementById('local').addEventListener('click', async () => {
  const { pendingDownload } = await chrome.storage.local.get('pendingDownload');
  if (pendingDownload) {
    chrome.downloads.resume(pendingDownload.id);
    chrome.storage.local.remove('pendingDownload');
    window.close();
  }
});

document.getElementById('remote').addEventListener('click', async () => {
  const { pendingDownload } = await chrome.storage.local.get('pendingDownload');
  if (pendingDownload) {
    const downloadId = pendingDownload.id;
    chrome.downloads.cancel(downloadId)
      .then(() => chrome.downloads.erase({ id: downloadId }));

    const finalUrl = pendingDownload.finalUrl;
    const fileName = finalUrl.substring(finalUrl.lastIndexOf('/') + 1);
    const response = await webSocketClient.downloadFile(finalUrl, fileName);
    console.log(response);
  }
});