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
  const [page, setPage] = useState<Page<HistoryFile> | undefined>(undefined);
  const [pendingDownloads, setPendingDownloads] = useState<PendingDownload[] | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (connected && !page && client) {
      client.getFilesHistory(0, 20)
        .then(page => setPage(page))
        .catch((error: ServerError) => setErrorMessage(error.message));
    } else if (!credentials) {
      if (page) {
        setPage(undefined);
      }
      if (errorMessage) {
        setErrorMessage('');
      }
    }
  }, [connected, client, credentials]);

  useEffect(() => {
    if (client) {
      client.registerHistoryReportHandler(report => {
        const newFiles = report.files;

        if (page) {
          const files = page.content;
          const result: HistoryFile[] = [...files];
          let totalElements = page.totalElements;
          for (const newFile of newFiles) {
            const index = files.findIndex(f => f.id === newFile.id);
            if (index === -1) {
              ++totalElements;
              result.unshift(newFile);
            } else {
              result[index] = newFile;
            }
          }
          setPage({ content: result, totalElements: totalElements });
        } else {
          setPage({ content: newFiles, totalElements: newFiles.length });
          return;
        }
      });
    }
  }, [client, page]);

  useEffect(() => {
    browserClient.getPendingDownloads()
      .then(downloads => setPendingDownloads(downloads))
  }, []);

  const context = {
    files: page?.content || [],
    totalFiles: page?.totalElements || 0,
    prependFile: (file: HistoryFile) => setPage({
      content: [file, ...(page?.content || [])],
      totalElements: (page?.totalElements || 0) + 1
    }),
    updateFile: (file: HistoryFile) => {
      if (page) {
        const updated = copyAndReplace(page.content, f => f.id === file.id, file);
        setPage({ content: updated, totalElements: page.totalElements });
      }
    },
    deleteFile: (id: string) => {
      if (page) {
        const updated = deleteElement(page.content, f => f.id === id);
        setPage({ content: updated, totalElements: page.totalElements - 1 });
      }
    },
    appendPage: (newPage: Page<HistoryFile>) => setPage({
      content: [...(page?.content || []), ...newPage.content],
      totalElements: newPage.totalElements
    })
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
      <DownloadingFiles setErrorMessage={setErrorMessage} />
      {credentials && !page && !errorMessage && <LoadingFileProgress />}
      {page && page.content.length === 0 && !errorMessage && pendingDownloads && pendingDownloads.length === 0 && connected && !failedToConnectReason && (
        <NoDownloads />
      )}
    </HistoryFilesContext.Provider>
  );
}

function DownloadingFiles({ setErrorMessage }: { setErrorMessage: (msg: string) => void }) {
  const { client } = useContext(ConnectionContext);
  const { files, totalFiles, appendPage } = useContext(HistoryFilesContext);
  const [fetching, setFetching] = useState(false);

  useEffect(() => {
    const handleScroll: EventListener = (e) => {
      if (window.innerHeight + document.documentElement.scrollTop !== document.documentElement.offsetHeight) {
        return;
      }
      if (fetching || totalFiles <= files.length || !client) {
        return;
      }

      setFetching(true);
      client.getFilesHistory(files.length, 20)
        .then(page => appendPage(page))
        .catch(e => setErrorMessage(e.message))
        .finally(() => setFetching(false));
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [fetching, files, totalFiles, client]);

  if (!files) {
    return null;
  }

  return (
    <>
      {files.map(file => mapFile(file))}
      {totalFiles > files.length && <LoadingFileProgress />}
    </>
  )
}

function mapFile(file: HistoryFile) {
  if (file.status === 'DOWNLOADED') {
    return <DownloadedFile key={file.id} file={file} />
  } else if (file.status === 'DOWNLOADING') {
    return <DownloadingFile key={file.id} file={file} />
  } else if (file.status === 'PAUSED') {
    return <PausedFile key={file.id} file={file} />
  } else if (file.status === 'ERROR') {
    return <ErrorFile key={file.id} file={file} />
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
  if (file.totalBytes > 0 && file.speedBytesPerMS !== 0) {
    const secondsRemaining = (file.totalBytes - file.downloadedBytes) / file.speedBytesPerMS / 1000;
    subtitle = buildTimeRemainingMessage(secondsRemaining);
  }

  return (
    <FileProgress
      fileName={file.name}
      downloadSpeed={buildSpeedMessage(file.speedBytesPerMS)}
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

function ErrorFile({ file }: { file: HistoryFile }) {
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
      variant="error"
      onDeleteHook={onDelete}
      key={file.id}
      buttonsDisabled={!connected || loading}
      errorMessage={errorMessage}
    />
  )
}


export default Downloads;
