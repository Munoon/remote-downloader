import {ConnectionContextType} from "../context.tsx";
import {UserCredentials} from "../browserClient.tsx";

const RETRIES_LIMIT = 3;

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
  private readonly credentials: UserCredentials
  private socket: WebSocket
  public handlers: WebSocketClientHandler
  private historyReportListeners?: (report: FilesHistoryReport) => void;
  private readonly messageHandlers: {
    [key: number]: {
      resolve: (msg: any) => void,
      reject: (msg: any) => void,
      retryTimeout: number
    }
  }
  private messageId: number
  private reconnectTimeout?: number

  constructor(credentials: UserCredentials, handlers: WebSocketClientHandler) {
    this.messageId = 0;
    this.messageHandlers = {};
    this.credentials = credentials;
    this.handlers = handlers;
    this.socket = this.buildWebSocket();
  }

  private buildWebSocket() {
    const socket = new WebSocket(`ws://${this.credentials.address}/websocket`);
    socket.binaryType = "arraybuffer";
    socket.onopen = () => this.onOpen();
    socket.onclose = () => this.onClose();
    socket.onmessage = (event: MessageEvent) => this.onSocketMessage(event);
    socket.onerror = (event: Event) => this.onError(event);
    return socket;
  }

  private async onOpen() {
    try {
      await this.login(this.credentials.username, this.credentials.passwordEncrypted);
      this.handlers.onOpen();
    } catch (error) {
      const serverError = error as ServerError;
      if (serverError.type === 'ALREADY_AUTHENTICATED') {
        // shouldn't happen, just in case
        this.handlers.onOpen();
      } else {
        this.handlers.onError(serverError);
      }
    }
  }

  private onSocketMessage(event: MessageEvent) {
    if (event.data instanceof ArrayBuffer) {
      const parsed = parseBinaryMessage(event.data);
      console.log('<===', parsed)
      if (parsed.id === 0) {
        this.handleServerMessage(parsed);
      } else {
        this.handleServerResponse(parsed);
      }
    } else {
      console.log("Received unknown message, ignoring it.", event)
    }
  }

  private handleServerMessage({ command, body }: Message) {
    switch (command) {
      case COMMANDS.FILES_HISTORY_REPORT:
        if (this.historyReportListeners) {
          this.historyReportListeners(JSON.parse(body));
        }
    }
  }

  private handleServerResponse(message: Message) {
    const handler = this.messageHandlers[message.id];
    if (handler) {
      if (handler.retryTimeout !== -1) {
        clearTimeout(handler.retryTimeout)
      }

      const body = message.body ? JSON.parse(message.body) : null;
      if (message.command === COMMANDS.ERROR) {
        handler.reject(body);
      } else {
        handler.resolve(body);
      }
      delete this.messageHandlers[message.id];
    }
  }

  private send(command: number, body: string, retryCount: number = 0): Promise<any> {
    const id = ++this.messageId;

    let promiseResolve: (val: any) => void;
    let promiseReject: (val: any) => void;
    const promise = new Promise<any>((resolve, reject) => {
      promiseResolve = resolve;
      promiseReject = reject;
    });

    // @ts-ignore
    const retryTimeout: number = retryCount < (RETRIES_LIMIT - 1)
      ? setTimeout(() => this.send(command, body, retryCount + 1), 1000)
      : -1;

    this.messageHandlers[id] = { resolve: promiseResolve!, reject: promiseReject!, retryTimeout };

    const binary = buildBinaryMessage({ id, command, body });
    this.socket.send(binary);

    console.log('===>', {id, command, body})

    return promise;
  }

  private onClose() {
    this.handlers.onClose();
    // @ts-ignore
    this.reconnectTimeout = setTimeout(() => {
      this.socket = this.buildWebSocket();
    })
  }

  private onError(event: Event) {
    console.log("Received WebSocket error", event);
    this.handlers.onError({ type: 'UNKNOWN', message: 'Connection failed.' });
    this.socket.close();
  }

  registerHistoryReportHandler(handler: (report: FilesHistoryReport) => void) {
    this.historyReportListeners = handler;
  }

  close() {
    this.socket.close();
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }
  }

  login(username: string, password: string) {
    return this.send(COMMANDS.LOGIN, JSON.stringify({ username, password, subscribeOnDownloadingFilesReport: true }));
  }

  downloadFile(url: string, fileName: string, path?: string): Promise<HistoryFile> {
    return this.send(COMMANDS.DOWNLOAD_URL, JSON.stringify({url, fileName, path}));
  }

  getFilesHistory(page: number, size: number): Promise<Page<HistoryFile>> {
    return this.send(COMMANDS.GET_FILES_HISTORY, JSON.stringify({page, size}));
  }

  stopDownloading(fileId: string): Promise<HistoryFile> {
    return this.send(COMMANDS.STOP_DOWNLOADING, JSON.stringify({fileId}));
  }

  resumeDownloading(fileId: string): Promise<HistoryFile> {
    return this.send(COMMANDS.RESUME_DOWNLOADING, JSON.stringify({fileId}));
  }

  deleteFile(fileId: string) {
    return this.send(COMMANDS.DELETE_FILE, JSON.stringify({ fileId }))
  }

  listFolders(path: string | null): Promise<ListFoldersResponse> {
    return this.send(COMMANDS.LIST_FOLDERS, JSON.stringify({path}));
  }
}

export const buildOnWebSocketClosedHandler = (setConnection: (connection: ConnectionContextType) => void) => () => {
  setConnection({
    connected: false,
    connecting: false,
    failedToConnectReason: 'Connection closed.',
    client: this,
    setConnection
  })
}

export const buildOnWebSocketErrorHandler = (setConnection: (connection: ConnectionContextType) => void) => (error: ServerError) => {
  setConnection({
    connected: false,
    connecting: false,
    failedToConnectReason: error.message,
    client: this,
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