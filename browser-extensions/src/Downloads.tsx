"use client";

import { FileProgress } from "@/ui/components/FileProgress";
import { useState, useEffect, useContext } from "react";
import client from "./api/client";
import { ConnectionContext, HistoryFilesContext } from "./context";
import { buildTimeRemainingMessage, buildSpeedMessage, copyAndReplace, deleteElement } from "./util";
import PendingDownloads from "./PendingDownloads";
import { LoadingFileProgress } from "./ui/components/LoadingFileProgress";

function Downloads() {
  const [files, setFiles] = useState<HistoryFile[] | undefined>(undefined);
  const { connected, connecting } = useContext(ConnectionContext);

  useEffect(() => {
    if (connected && !files) {
      client.getFilesHistory(0, 20)
        .then(files => setFiles(files.content));
    }
  }, [connected]);

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
      <PendingDownloads />
      {files && files.map(file => mapFile(file))}
      {connecting && <LoadingFileProgress />}
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

  const onDelete = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    client.deleteFile(file.id)
      .then(() => filesContext.deleteFile(file.id));
  };

  const onPause = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    client.stopDownloading(file.id)
      .then(file => filesContext.updateFile(file));
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

  const onDelete = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    client.deleteFile(file.id)
      .then(() => filesContext.deleteFile(file.id));
  };

  const onContinue = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    client.resumeDownloading(file.id)
      .then(file => filesContext.updateFile(file));
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

  const onDelete = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    client.deleteFile(file.id)
      .then(() => filesContext.deleteFile(file.id));
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
