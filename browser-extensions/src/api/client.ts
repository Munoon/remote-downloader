type Message = { id: number, command: number, body: any }

const COMMANDS = {
  SERVER_HELLO: 1,
  ERROR: 2,
  DOWNLOAD_URL: 4,
  GET_FILES_HISTORY: 5,
  DELETE_FILE: 6,
  STOP_DOWNLOADING: 7,
  RESUME_DOWNLOADING: 8
};

class WebSocketClient {
  private readonly socket: WebSocket
  private messageId: number
  private messageHandlers: {
    [key: number]: { resolve: (msg: Message) => void, reject: (msg: Message) => void }
  }
  private serverHelloPromise: { resolve: (msg: ServerHello) => void, reject: (msg: Message) => void, promise: Promise<ServerHello> }
  private serverHello: ServerHello | null = null

  constructor(url: string) {
    this.messageId = 0;
    this.messageHandlers = {};

    let promiseResolve: (val: ServerHello) => void;
    let promiseReject: (val: Message) => void;
    const promise = new Promise<ServerHello>((resolve, reject) => {
      promiseResolve = resolve;
      promiseReject = reject;
    });
    this.serverHelloPromise = { resolve: promiseResolve!, reject: promiseReject!, promise }

    this.socket = new WebSocket(url);
    this.socket.binaryType = "arraybuffer";
    this.socket.onmessage = (event: MessageEvent) => this._onSocketMessage(event);
    this.socket.onerror = (event: Event) => this._onSocketError(event);
    this.socket.onclose = () => this._onSocketClose();
  }

  _onSocketMessage(event: MessageEvent) {
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

  _onSocketError(event: Event) {
    console.log("WebSocket error: ", event)
    this.socket.close();
  }

  _onSocketClose() {
    // TODO impl
  }

  _handleServerMessage({ command, body }: Message) {
    switch (command) {
      case COMMANDS.SERVER_HELLO:
        this.serverHello = JSON.parse(body);
        this.serverHelloPromise.resolve(this.serverHello as ServerHello);
        break;
    }
  }

  _handleServerResponse(message: Message) {
    const handler = this.messageHandlers[message.id];
    if (handler) {
      const body = JSON.parse(message.body);
      if (message.command === COMMANDS.ERROR) {
        handler.reject({ ...message, body })
      } else {
        handler.resolve({ ...message, body });
      }
      delete this.messageHandlers[message.id];
    }
  }

  _send(command: number, body: string): Promise<Message> {
    const id = ++this.messageId;

    let promiseResolve: (val: Message) => void;
    let promiseReject: (val: Message) => void;
    const promise = new Promise<Message>((resolve, reject) => {
      promiseResolve = resolve;
      promiseReject = reject;
    });

    this.messageHandlers[id] = { resolve: promiseResolve!!, reject: promiseReject!! };

    const binary = buildBinaryMessage({ id, command, body });
    this.socket.send(binary);

    return promise;
  }

  getServerHello() {
    if (this.serverHello) {
      return Promise.resolve(this.serverHello);
    }
    return this.serverHelloPromise.promise;
  }

  downloadFile(url: string, fileName: string): Promise<Message> {
    return this._send(COMMANDS.DOWNLOAD_URL, JSON.stringify({ url, fileName }));
  }

  getFilesHistory(page: number, size: number): Promise<Page<HistoryFile>> {
    return this._send(COMMANDS.GET_FILES_HISTORY, JSON.stringify({ page, size }))
      .then(msg => msg.body);
  }

  stopDownloading(fileId: string) {
    return this._send(COMMANDS.STOP_DOWNLOADING, JSON.stringify({ fileId }))
  }

  resumeDownloading(fileId: string) {
    return this._send(COMMANDS.RESUME_DOWNLOADING, JSON.stringify({ fileId }))
  }

  deleteFile(fileId: string) {
    return this._send(COMMANDS.DELETE_FILE, JSON.stringify({ fileId }))
  }
}

function buildBinaryMessage({ id, command, body }: Message) {
  const encoder = new TextEncoder();
  const textBytes = encoder.encode(body);
  const buffer = new ArrayBuffer(6 + textBytes.length);
  const view = new DataView(buffer);

  view.setUint32(0, id, false); // big-endian
  view.setUint16(4, command, false);   // big-endian

  new Uint8Array(buffer, 6).set(textBytes);
  return buffer;
}

function parseBinaryMessage(buffer: ArrayBuffer): Message {
  const view = new DataView(buffer);
  const decoder = new TextDecoder("utf-8");

  const id = view.getUint32(0, false);
  const command = view.getUint16(4, false);

  const bodyBytes = new Uint8Array(buffer, 6);
  const body = decoder.decode(bodyBytes);

  return { id, command, body };
}

export default new WebSocketClient('ws://127.0.0.1:8080/websocket');