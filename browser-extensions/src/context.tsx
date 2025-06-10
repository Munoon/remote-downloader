import { createContext } from "react";
import { PendingDownload } from "./browser_client";

export const HistoryFilesContext = createContext<{
  files: HistoryFile[],
  reload: () => void,
  prependFile: (file: HistoryFile) => void
}>({ files: [], reload: () => {}, prependFile: () => {} });

export const PendingDownloadContext = createContext<{
  pendingDownloads: PendingDownload[],
  setPendingDownloads: (pendingDownloads: PendingDownload[]) => void
}>({ pendingDownloads: [], setPendingDownloads: () => {} });

export const DownloadFilePathContext = createContext<{
  filePath: string[],
  setFilePath: (filePath: string[]) => void
}>({ filePath: [], setFilePath: () => {} });