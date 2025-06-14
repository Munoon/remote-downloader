import {ConnectionContextType} from "../context.tsx";
import {UserCredentials} from "../browserClient.tsx";

type Message = { id: number, command: number, body?: any }

const COMMANDS = {
  LOGIN: 1,
  ERROR: 2,
  FILES_HISTORY_REPORT: 3,
  DOWNLOAD_URL: 4,
  GET_FILES_HISTORY: 5,
  DELETE_FILE: 6,
  STOP_DOWNLOADING: 7,
  RESUME_DOWNLOADING: 8,
  LIST_FOLDERS: 9
};

interface WebSocketClientHandler {
  onOpen: () => void;
  onClose: () => void;
  onError: (error: ServerError) => void;
}

export default class WebSocketClient {
  private readonly socket: WebSocket
  private historyReportListeners?: (report: FilesHistoryReport) => void;
  private readonly messageHandlers: {
    [key: number]: { resolve: (msg: any) => void, reject: (msg: any) => void }
  }
  public handlers: WebSocketClientHandler
  private messageId: number

  constructor(url: string, credentials: UserCredentials, handlers: WebSocketClientHandler) {
    this.messageId = 0;
    this.messageHandlers = {};
    this.handlers = handlers;

    this.socket = new WebSocket(url);
    this.socket.binaryType = "arraybuffer";
    this.socket.onopen = async () => {
      try {
        await this.login(credentials.username, credentials.passwordEncrypted);
        handlers.onOpen();
      } catch (error) {
        handlers.onError(error as ServerError);
      }
    };
    this.socket.onclose = () => this.handlers.onClose();
    this.socket.onmessage = (event: MessageEvent) => this._onSocketMessage(event);
    this.socket.onerror = (event: Event) => {
      console.log("Received WebSocket error", event);
      this.handlers.onError({ type: 'UNKNOWN', message: 'Connection failed.' });
      this.socket.close();
    }
  }

  _onSocketMessage(event: MessageEvent) {
    if (event.data instanceof ArrayBuffer) {
      const parsed = parseBinaryMessage(event.data);
      console.log('<===', parsed)
      if (parsed.id === 0) {
        this._handleServerMessage(parsed);
      } else {
        this._handleServerResponse(parsed);
      }
    } else {
      console.log("Received unknown message, ignoring it.", event)
    }
  }

  _handleServerMessage({ command, body }: Message) {
    switch (command) {
      case COMMANDS.FILES_HISTORY_REPORT:
        if (this.historyReportListeners) {
          this.historyReportListeners(JSON.parse(body));
        }
    }
  }

  _handleServerResponse(message: Message) {
    const handler = this.messageHandlers[message.id];
    if (handler) {
      const body = message.body ? JSON.parse(message.body) : null;
      if (message.command === COMMANDS.ERROR) {
        handler.reject(body);
      } else {
        handler.resolve(body);
      }
      delete this.messageHandlers[message.id];
    }
  }

  _send(command: number, body: string): Promise<any> {
    const id = ++this.messageId;

    let promiseResolve: (val: any) => void;
    let promiseReject: (val: any) => void;
    const promise = new Promise<any>((resolve, reject) => {
      promiseResolve = resolve;
      promiseReject = reject;
    });

    this.messageHandlers[id] = { resolve: promiseResolve!, reject: promiseReject! };

    const binary = buildBinaryMessage({ id, command, body });
    this.socket.send(binary);

    console.log('===>', {id, command, body})

    return promise;
  }

  registerHistoryReportHandler(handler: (report: FilesHistoryReport) => void) {
    this.historyReportListeners = handler;
  }

  login(username: string, password: string) {
    return this._send(COMMANDS.LOGIN, JSON.stringify({ username, password, subscribeOnDownloadingFilesReport: true }));
  }

  downloadFile(url: string, fileName: string, path?: string): Promise<HistoryFile> {
    return this._send(COMMANDS.DOWNLOAD_URL, JSON.stringify({url, fileName, path}));
  }

  getFilesHistory(page: number, size: number): Promise<Page<HistoryFile>> {
    return this._send(COMMANDS.GET_FILES_HISTORY, JSON.stringify({page, size}));
  }

  stopDownloading(fileId: string): Promise<HistoryFile> {
    return this._send(COMMANDS.STOP_DOWNLOADING, JSON.stringify({fileId}));
  }

  resumeDownloading(fileId: string): Promise<HistoryFile> {
    return this._send(COMMANDS.RESUME_DOWNLOADING, JSON.stringify({fileId}));
  }

  deleteFile(fileId: string) {
    return this._send(COMMANDS.DELETE_FILE, JSON.stringify({ fileId }))
  }

  listFolders(path: string | null): Promise<ListFoldersResponse> {
    return this._send(COMMANDS.LIST_FOLDERS, JSON.stringify({path}));
  }
}

export const buildOnWebSocketClosedHandler = (setConnection: (connection: ConnectionContextType) => void) => () => {
  setConnection({
    connected: false,
    connecting: false,
    failedToConnectReason: 'Connection closed.',
    client: undefined,
    setConnection
  })
}

export const buildOnWebSocketErrorHandler = (setConnection: (connection: ConnectionContextType) => void) => (error: ServerError) => {
  setConnection({
    connected: false,
    connecting: false,
    failedToConnectReason: error.message,
    client: undefined,
    setConnection
  })
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