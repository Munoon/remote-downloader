import { createContext } from "react";
import { PendingDownload, UserCredentials } from "./browserClient.tsx";
import WebSocketClient from "./api/client";

export const HistoryFilesContext = createContext<{
  files: HistoryFile[],
  totalFiles: number,
  prependFile: (file: HistoryFile) => void,
  updateFile: (file: HistoryFile) => void,
  deleteFile: (id: string) => void,
  appendPage: (page: Page<HistoryFile>) => void
}>({
  files: [],
  totalFiles: 0,
  prependFile: () => {},
  updateFile: () => {},
  deleteFile: () => {},
  appendPage: () => {}
});

export const PendingDownloadContext = createContext<{
  pendingDownloads: PendingDownload[],
  setPendingDownloads: (pendingDownloads: PendingDownload[]) => void
}>({ pendingDownloads: [], setPendingDownloads: () => {} });

export const DownloadFilePathContext = createContext<{
  filePath: string[],
  setFilePath: (filePath: string[]) => void
}>({ filePath: [], setFilePath: () => {} });

export interface ConnectionContextType {
  connected: boolean
  connecting: boolean
  failedToConnectReason?: string
  client?: WebSocketClient,
  setConnection: (connection: ConnectionContextType) => void
}
export const ConnectionContext = createContext<ConnectionContextType>({
  connected: false,
  connecting: true,
  failedToConnectReason: undefined,
  setConnection: () => {}
});

export const UserCredentialsContext = createContext<{
  credentials?: UserCredentials,
  setCredentials: (credentials?: UserCredentials) => void
}>({ setCredentials: () => {} })