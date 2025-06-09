import { useContext, useEffect, useState } from "react";
import { DownloadPrompt } from "./ui";
import { resolveFileNameFromURL } from "./util";
import client from "./api/client";
import { HistoryFilesContext } from "./context";

declare const chrome: any;
const PENDING_DOWNLOADS_STORAGE_KEY = 'pendingDownloads';
interface PendingDownload {
  id: number
  url?: string
  finalUrl?: string
  fileSize?: number
  filename?: string
}

export default function PendingDownloads() {
  const [pendingDownloads, setPendingDownloads] = useState<PendingDownload[]>([]);
  useEffect(() => {
    getPendingDownloads()
      .then(downloads => setPendingDownloads(downloads));
  }, []);

  const onRefresh = (pendingDownloads: PendingDownload[]) => setPendingDownloads(pendingDownloads);

  return (
    <>
      {pendingDownloads.map(download => <PendingDownload pendingDownload={download} key={download.id} onRefresh={onRefresh} />)}
    </>
  );
}

function PendingDownload({ pendingDownload, onRefresh }: { pendingDownload: PendingDownload, onRefresh: (downloads: PendingDownload[]) => void }) {
  const url = pendingDownload.finalUrl || pendingDownload.url;
  const [fileName, setFileName] = useState(pendingDownload.filename || (url ? resolveFileNameFromURL(url) : ''));
  const [loading, setLoading] = useState(false);
  const historyFilesContext = useContext(HistoryFilesContext);
  
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
    chrome.downloads.resume(pendingDownload.id);
    removePendingDownload(pendingDownload.id)
      .then(pendingDownloads => onRefresh(pendingDownloads));
  }

  const onDownloadRemotely = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    client.downloadFile(url, fileName)
      .then(newFile => { 
        historyFilesContext.prependFile(newFile);

        removePendingDownload(pendingDownload.id)
          .then(pendingDownloads => onRefresh(pendingDownloads));
      });
  }

  const onDelete = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    removePendingDownload(pendingDownload.id)
      .then(pendingDownloads => onRefresh(pendingDownloads));
  }

  return (
    <DownloadPrompt
      fileName={fileName}
      onFileNameChange={onFileNameChange}
      onDownloadLocaly={onDownloadLocaly}
      onDownloadRemotely={onDownloadRemotely}
      onDelete={onDelete}
      disabled={loading}
      />
  )
}

async function removePendingDownload(id: number): Promise<PendingDownload[]> {
  const pendingDownloads = await getPendingDownloads();
  if (pendingDownloads.length === 1 && pendingDownloads[0].id === id) {
    chrome.storage.local.remove(PENDING_DOWNLOADS_STORAGE_KEY);
  } else {
    const index = pendingDownloads.findIndex(download => download.id === id);
    if (index !== -1) {
      pendingDownloads.splice(index, 1);
      chrome.storage.local.set({ [PENDING_DOWNLOADS_STORAGE_KEY]: pendingDownloads });
      return pendingDownloads;
    }
  }
  return [];
}

async function getPendingDownloads(): Promise<PendingDownload[]> {
  const { pendingDownloads } = await chrome.storage.local.get(PENDING_DOWNLOADS_STORAGE_KEY);
  return pendingDownloads || [];
}