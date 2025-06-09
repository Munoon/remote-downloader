"use client";

import { DefaultPageLayout } from "@/ui/layouts/DefaultPageLayout";
import { FileProgress } from "@/ui/components/FileProgress";
import { useState, useEffect, useContext } from "react";
import client from "./api/client";
import { HistoryFilesContext } from "./context";
import { buildTimeRemainingMessage, buildSpeedMessage } from "./util";

function Downloads() {
  const [files, setFiles] = useState<HistoryFile[] | undefined>(undefined);
  const [reloadTrigger, setReloadTrigger] = useState(0);
  
  useEffect(() => {
    client.getFilesHistory(0, 20)
      .then(files => setFiles(files.content));
  }, [reloadTrigger]);

  const context = {
    files: files || [],
    reload: () => setReloadTrigger(reloadTrigger + 1)
  };

  return (
    <HistoryFilesContext.Provider value={context}>
      <DefaultPageLayout>
        <div className="flex w-96 flex-col items-start gap-3 bg-default-background px-3 py-3">
          {files && files.map(file => mapFile(file))}
        </div>
      </DefaultPageLayout>
    </HistoryFilesContext.Provider>
  );
}

function mapFile(file: HistoryFile) {
  console.log(file);
  if (file.status === 'DOWNLOADED') {
    return <DownloadedFile file={file} />
  } else if (file.status === 'DOWNLOADING') {
    return <DownloadingFile file={file} />
  } else if (file.status === 'PAUSED') {
    return <PausedFile file={file} />
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

  const secondsRemaining = (file.totalBytes - file.downloadedBytes) / file.speedBytesPerSecond;

  return (
    <FileProgress
      fileName={file.name}
      downloadSpeed={buildSpeedMessage(file.speedBytesPerSecond)}
      subtitle={buildTimeRemainingMessage(secondsRemaining)}
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
