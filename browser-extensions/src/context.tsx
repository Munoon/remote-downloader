import { createContext } from "react";

export const HistoryFilesContext = createContext<{
  files: HistoryFile[],
  reload: () => void,
  prependFile: (file: HistoryFile) => void
}>({ files: [], reload: () => {}, prependFile: () => {} });