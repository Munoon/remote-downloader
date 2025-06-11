import { createContext } from "react";
import { PendingDownload } from "./browser_client";
import WebSocketClient from "./api/client";

export const HistoryFilesContext = createContext<{
  files: HistoryFile[],
  prependFile: (file: HistoryFile) => void,
  updateFile: (file: HistoryFile) => void,
  deleteFile: (id: string) => void
}>({ files: [], prependFile: () => {}, updateFile: () => {}, deleteFile: () => {} });

export const PendingDownloadContext = createContext<{
  pendingDownloads: PendingDownload[],
  setPendingDownloads: (pendingDownloads: PendingDownload[]) => void
}>({ pendingDownloads: [], setPendingDownloads: () => {} });

export const DownloadFilePathContext = createContext<{
  filePath: string[],
  setFilePath: (filePath: string[]) => void
}>({ filePath: [], setFilePath: () => {} });

export interface ConnectionContextType {
  authenticated: boolean
  connected: boolean
  connecting: boolean
  failedToConnectReason?: string
  client?: WebSocketClient
}
export const ConnectionContext = createContext<ConnectionContextType>({
  authenticated: false,
  connected: false,
  connecting: true,
  failedToConnectReason: undefined
});