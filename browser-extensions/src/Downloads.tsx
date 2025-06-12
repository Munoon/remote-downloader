"use client";

import { FileProgress } from "@/ui/components/FileProgress";
import {useState, useEffect, useContext, MouseEventHandler} from "react";
import { ConnectionContext, HistoryFilesContext, PendingDownloadContext, UserCredentialsContext } from "./context";
import { buildTimeRemainingMessage, buildSpeedMessage, copyAndReplace, deleteElement } from "./util";
import PendingDownloadComponent from "./PendingDownload";
import { LoadingFileProgress } from "./ui/components/LoadingFileProgress";
import browserClient, { PendingDownload } from "./browserClient.tsx";
import { NoDownloads } from "./ui/components/NoDownloads";
import { ErrorMessage } from "./ui/components/ErrorMessage";
import ConnectionError from "./ConnectionError";

function Downloads() {
  const { connected, client, failedToConnectReason } = useContext(ConnectionContext);
  const { credentials } = useContext(UserCredentialsContext);
  const [files, setFiles] = useState<HistoryFile[] | undefined>(undefined);
  const [pendingDownloads, setPendingDownloads] = useState<PendingDownload[] | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (connected && !files && client) {
      client.getFilesHistory(0, 20)
        .then(files => setFiles(files.content))
        .catch((error: ServerError) => setErrorMessage(error.message));
    } else if (!credentials) {
      if (files) {
        setFiles(undefined);
      }
      if (errorMessage) {
        setErrorMessage('');
      }
    }
  }, [connected, client, credentials]);

  useEffect(() => {
    browserClient.getPendingDownloads()
      .then(downloads => setPendingDownloads(downloads))
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

      <ConnectionError />
      {errorMessage && <ErrorMessage text={errorMessage} />}
      {files && files.map(file => mapFile(file))}
      {!files && connected && <LoadingFileProgress />}
      {files && files.length === 0 && !errorMessage && pendingDownloads && pendingDownloads.length === 0 && connected && !failedToConnectReason && (
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
  const { client, connected } = useContext(ConnectionContext);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const onDelete: MouseEventHandler<HTMLButtonElement> = (e) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      if (errorMessage) {
        setErrorMessage('');
      }

      client.deleteFile(file.id)
        .then(() => filesContext.deleteFile(file.id))
        .catch((error: ServerError) => {
          setLoading(false);
          setErrorMessage(error.message);
        });
    }
  };

  const onPause: MouseEventHandler<HTMLButtonElement> = (e) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      if (errorMessage) {
        setErrorMessage('');
      }

      client.stopDownloading(file.id)
        .then(file => filesContext.updateFile(file))
        .catch((error: ServerError) => {
          setLoading(false);
          setErrorMessage(error.message);
        });
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
      buttonsDisabled={!connected || loading}
      errorMessage={errorMessage}
      />
  );
}

function PausedFile({ file }: { file: HistoryFile }) {
  const filesContext = useContext(HistoryFilesContext);
  const { client, connected } = useContext(ConnectionContext);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const onDelete: MouseEventHandler<HTMLButtonElement> = (e) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      if (errorMessage) {
        setErrorMessage('');
      }

      client.deleteFile(file.id)
        .then(() => filesContext.deleteFile(file.id))
        .catch((error: ServerError) => {
          setLoading(false);
          setErrorMessage(error.message);
        });
    }
  };

  const onContinue: MouseEventHandler<HTMLButtonElement> = (e) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      if (errorMessage) {
        setErrorMessage('');
      }

      client.resumeDownloading(file.id)
        .then(file => filesContext.updateFile(file))
        .catch((error: ServerError) => {
          setLoading(false);
          setErrorMessage(error.message);
        });
    }
  };

  return (
    <FileProgress
      fileName={file.name}
      variant="paused"
      onDeleteHook={onDelete}
      onContinueHook={onContinue}
      key={file.id}
      buttonsDisabled={!connected || loading}
      progress={(file.downloadedBytes / file.totalBytes) * 100}
      errorMessage={errorMessage}
      />
  );
}

function DownloadedFile({ file }: { file: HistoryFile }) {
  const filesContext = useContext(HistoryFilesContext);
  const { client, connected } = useContext(ConnectionContext);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const onDelete: MouseEventHandler<HTMLButtonElement> = (e) => {
    e.preventDefault();
    if (client) {
      setLoading(true);
      if (errorMessage) {
        setErrorMessage('');
      }

      client.deleteFile(file.id)
        .then(() => filesContext.deleteFile(file.id))
        .catch((error: ServerError) => {
          setLoading(false);
          setErrorMessage(error.message);
        });
    }
  };

  return (
    <FileProgress
      fileName={file.name}
      variant="downloaded"
      onDeleteHook={onDelete}
      key={file.id}
      buttonsDisabled={!connected || loading}
      errorMessage={errorMessage}
      />
  )
}

export default Downloads;
