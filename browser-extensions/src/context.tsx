import { createContext } from "react";

export const HistoryFilesContext = createContext<{
  files: HistoryFile[],
  reload: () => void
}>({ files: [], reload: () => {} });