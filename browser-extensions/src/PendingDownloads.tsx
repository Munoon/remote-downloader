import { useContext, useEffect, useState, createContext } from "react";
import { DownloadPrompt } from "./ui";
import { resolveFileNameFromURL } from "./util";
import client from "./api/client";
import { DownloadFilePathContext, HistoryFilesContext, PendingDownloadContext } from "./context";
import browserClient, { PendingDownload } from "./browser_client";
import FilePathSelector from "./FilePathSelector";

export default function PendingDownloads() {
  const [pendingDownloads, setPendingDownloads] = useState<PendingDownload[]>([]);
  useEffect(() => {
    browserClient.getPendingDownloads()
      .then(downloads => setPendingDownloads(downloads));
  }, []);

  return (
    <PendingDownloadContext.Provider value={{ pendingDownloads, setPendingDownloads }}>
      {pendingDownloads.map(download => <DownloadProposal pendingDownload={download} key={download.id} />)}
    </PendingDownloadContext.Provider>
  );
}

function DownloadProposal({ pendingDownload }: { pendingDownload: PendingDownload }) {
  const url = pendingDownload.finalUrl || pendingDownload.url;
  const [fileName, setFileName] = useState(pendingDownload.filename || (url ? resolveFileNameFromURL(url) : ''));
  const [filePath, setFilePath] = useState<string[]>(['Root']);
  const [loading, setLoading] = useState(false);
  const historyFilesContext = useContext(HistoryFilesContext);
  const { setPendingDownloads } = useContext(PendingDownloadContext);
  
  if (!url) {
    return;
  }
  
  const onFileNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    setFileName(e.target.value);
  }

  const onDownloadLocaly = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    browserClient.resumeDownload(pendingDownload);
    browserClient.removePendingDownload(pendingDownload)
      .then(pendingDownloads => setPendingDownloads(pendingDownloads));
  }

  const onDownloadRemotely = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    
    const path = filePath.length === 1 ? undefined : filePath.slice(1).join('/');
    client.downloadFile(url, fileName, path)
      .then(newFile => { 
        historyFilesContext.prependFile(newFile);

        browserClient.removePendingDownload(pendingDownload)
          .then(pendingDownloads => setPendingDownloads(pendingDownloads));
      });
  }

  const onDelete = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    browserClient.removePendingDownload(pendingDownload)
      .then(pendingDownloads => setPendingDownloads(pendingDownloads));
  }

  const filePathElement = (
    <DownloadFilePathContext.Provider value={{ filePath, setFilePath }}>
      <FilePathSelector />
    </DownloadFilePathContext.Provider>
  );

  return (
    <DownloadPrompt
      fileName={fileName}
      filePath={filePath.join('/')}
      onFileNameChange={onFileNameChange}
      onDownloadLocaly={onDownloadLocaly}
      onDownloadRemotely={onDownloadRemotely}
      onDelete={onDelete}
      filePathElement={filePathElement}
      disabled={loading}
      />
  )
}