"use client";

import { FileProgress } from "@/ui/components/FileProgress";
import { useState, useEffect, useContext } from "react";
import { ConnectionContext, HistoryFilesContext, PendingDownloadContext, UserCredentialsContext } from "./context";
import { buildTimeRemainingMessage, buildSpeedMessage, copyAndReplace, deleteElement } from "./util";
import PendingDownloadComponent from "./PendingDownload";
import { LoadingFileProgress } from "./ui/components/LoadingFileProgress";
import browserClient, { PendingDownload } from "./browser_client";
import { NoDownloads } from "./ui/components/NoDownloads";

function Downloads() {
  const { connected, connecting, client } = useContext(ConnectionContext);
  const { credentials } = useContext(UserCredentialsContext);
  const [files, setFiles] = useState<HistoryFile[] | undefined>(undefined);
  const [pendingDownloads, setPendingDownloads] = useState<PendingDownload[] | null>(null);

  useEffect(() => {
    if (connected && !files && client) {
      client.getFilesHistory(0, 20)
        .then(files => setFiles(files.content));
    } else if (!credentials && files) {
      setFiles(undefined);
    }
  }, [connected, client, credentials]);

  useEffect(() => {
    browserClient.getPendingDownloads()
      .then(downloads => setPendingDownloads(downloads));
  }, []);

  const context = {
    files: files || [],
    prependFile: (file: HistoryFile) => setFiles([file, ...(files || [])]),
    updateFile: (file: HistoryFile) => {
      if (files) {
        const updated = copyAndReplace(files, f => f.id === file.id, file);
        setFiles(updated);
      }
    },
    deleteFile: (id: string) => {
      if (files) {
        const updated = deleteElement(files, f => f.id === id);
        setFiles(updated);
      }
    }
  };

  return (
    <HistoryFilesContext.Provider value={context}>
      <PendingDownloadContext.Provider value={{ pendingDownloads: pendingDownloads || [], setPendingDownloads }}>
        {pendingDownloads && pendingDownloads.map(download =>
          <PendingDownloadComponent pendingDownload={download} key={download.id} />
        )}
      </PendingDownloadContext.Provider>

      {files && files.map(file => mapFile(file))}
      {connecting && !files && <LoadingFileProgress />}
      {files && files.length === 0 && pendingDownloads && pendingDownloads.length === 0 && connected && credentials && (
        <NoDownloads />
      )}
    </HistoryFilesContext.Provider>
  );
}

function mapFile(file: HistoryFile) {
  if (file.status === 'DOWNLOADED') {
    return <DownloadedFile key={file.id} file={file} />
  } else if (file.status === 'DOWNLOADING') {
    return <DownloadingFile key={file.id} file={file} />
  } else if (file.status === 'PAUSED') {
    return <PausedFile key={file.id} file={file} />
  }
}

function DownloadingFile({ file }: { file: HistoryFile }) {
  const filesContext = useContext(HistoryFilesContext);
  const [loading, setLoading] = useState(false);
  const { client } = useContext(ConnectionContext);

  const onDelete = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      client.deleteFile(file.id)
        .then(() => filesContext.deleteFile(file.id));  
    }
  };

  const onPause = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      client.stopDownloading(file.id)
        .then(file => filesContext.updateFile(file));
    }
  };

  let subtitle;
  if (file.totalBytes > 0 && file.speedBytesPerSecond !== 0) {
    const secondsRemaining = (file.totalBytes - file.downloadedBytes) / file.speedBytesPerSecond;
    subtitle = buildTimeRemainingMessage(secondsRemaining);
  }

  return (
    <FileProgress
      fileName={file.name}
      downloadSpeed={buildSpeedMessage(file.speedBytesPerSecond)}
      subtitle={subtitle}
      progress={(file.downloadedBytes / file.totalBytes) * 100}
      variant="downloading"
      onDeleteHook={onDelete}
      onPauseHook={onPause}
      key={file.id}
      buttonsDisabled={loading}
      />
  );
}

function PausedFile({ file }: { file: HistoryFile }) {
  const filesContext = useContext(HistoryFilesContext);
  const [loading, setLoading] = useState(false);
  const { client } = useContext(ConnectionContext);

  const onDelete = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      client.deleteFile(file.id)
        .then(() => filesContext.deleteFile(file.id));
    }
  };

  const onContinue = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      client.resumeDownloading(file.id)
        .then(file => filesContext.updateFile(file));
    }
  };

  return (
    <FileProgress
      fileName={file.name}
      variant="paused"
      onDeleteHook={onDelete}
      onContinueHook={onContinue}
      key={file.id}
      buttonsDisabled={loading}
      progress={(file.downloadedBytes / file.totalBytes) * 100}
      />
  );
}

function DownloadedFile({ file }: { file: HistoryFile }) {
  const filesContext = useContext(HistoryFilesContext);
  const [loading, setLoading] = useState(false);
  const { client } = useContext(ConnectionContext);

  const onDelete = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      client.deleteFile(file.id)
        .then(() => filesContext.deleteFile(file.id));
    }
  };

  return (
    <FileProgress
      fileName={file.name}
      variant="downloaded"
      onDeleteHook={onDelete}
      key={file.id}
      buttonsDisabled={loading}
      />
  )
}

export default Downloads;
