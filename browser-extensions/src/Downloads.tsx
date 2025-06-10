"use client";

import { FileProgress } from "@/ui/components/FileProgress";
import { useState, useEffect, useContext } from "react";
import client from "./api/client";
import { HistoryFilesContext } from "./context";
import { buildTimeRemainingMessage, buildSpeedMessage } from "./util";
import PendingDownloads from "./PendingDownloads";

function Downloads() {
  const [files, setFiles] = useState<HistoryFile[] | undefined>(undefined);
  const [reloadTrigger, setReloadTrigger] = useState(0);
  
  useEffect(() => {
    client.getFilesHistory(0, 20)
      .then(files => setFiles(files.content));
  }, [reloadTrigger]);

  const context = {
    files: files || [],
    reload: () => setReloadTrigger(reloadTrigger + 1),
    prependFile: (file: HistoryFile) => setFiles([file, ...(files || [])])
  };

  return (
    <HistoryFilesContext.Provider value={context}>
      <PendingDownloads />
      {files && files.map(file => mapFile(file))}
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
      .then(() => filesContext.reload());
  };

  const onPause = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    client.stopDownloading(file.id)
      .then(() => filesContext.reload());
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
      .then(() => filesContext.reload());
  };

  const onContinue = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    client.resumeDownloading(file.id)
      .then(() => filesContext.reload());
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
      .then(() => filesContext.reload());
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
