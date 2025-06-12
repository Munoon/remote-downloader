import React, { useContext, useEffect, useState, createContext, MouseEventHandler } from "react";
import { DownloadPrompt } from "./ui";
import { resolveFileNameFromURL } from "./util";
import { ConnectionContext, ConnectionContextType, DownloadFilePathContext, HistoryFilesContext, PendingDownloadContext, UserCredentialsContext } from "./context";
import browserClient, { PendingDownload, UserCredentials } from "./browser_client";
import FilePathSelector from "./FilePathSelector";
import { Button } from "./ui/components/Button";
import { Tooltip as MessageTooltip } from "./ui/components/Tooltip";
import { FeatherCloud, Tooltip } from "@subframe/core";
import { Loader } from "./ui/components/Loader";

export default function PendingDownloadComponent({ pendingDownload }: { pendingDownload: PendingDownload }) {
  const [fileName, setFileName] = useState(buildDefaultFileName(pendingDownload));
  const [filePath, setFilePath] = useState<string[]>(['Root']);
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [fileNameErrorMessage, setFileNameErrorMessage] = useState('');
  const historyFilesContext = useContext(HistoryFilesContext);
  const { setPendingDownloads } = useContext(PendingDownloadContext);
  const { credentials } = useContext(UserCredentialsContext);
  const connection = useContext(ConnectionContext);

  useEffect(() => {
    if (!credentials) {
      setFilePath(['Root'])
    }
  }, [credentials])

  function validateFileName(fileName: string): boolean {
    if (fileName.length === 0) {
      setFileNameErrorMessage('File name is empty.');
      return false;
    }

    if (fileName.length > 255) {
      setFileNameErrorMessage('File name is too long (limit is 255 characters).');
      return false;
    }

    if (fileName.includes('/') || fileName.includes('\0')) {
      setFileNameErrorMessage("File name cannot contain '/' character.");
      return false;
    }

    if (fileNameErrorMessage) {
      setFileNameErrorMessage('');
    }
    return true;
  }

  const onFileNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();

    const fileName = e.target.value;
    setFileName(fileName);
    if (fileNameErrorMessage) {
      validateFileName(fileName);
    }
  }

  const onDownloadLocally = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    browserClient.resumeDownload(pendingDownload);
    browserClient.removePendingDownload(pendingDownload)
      .then(pendingDownloads => setPendingDownloads(pendingDownloads));
  }

  const onDownloadRemotely = (e: { preventDefault: () => void }) => {
    e.preventDefault();
    
    const url = pendingDownload.finalUrl || pendingDownload.url;
    if (!url || !connection.client) { // shouldn't happen
      setErrorMessage('Failed to resolve the file URL.');
      return;
    }

    if (!validateFileName(fileName)) {
      return;
    }

    setLoading(true);
    if (errorMessage) {
      setErrorMessage('');
    }

    const path = filePath.length === 1 ? undefined : filePath.slice(1).join('/');
    connection.client.downloadFile(url, fileName, path)
      .then(newFile => { 
        historyFilesContext.prependFile(newFile);

        browserClient.removePendingDownload(pendingDownload)
          .then(pendingDownloads => setPendingDownloads(pendingDownloads));
      })
      .catch((error: ServerError) => {
        setLoading(false);
        setErrorMessage(error.message);
      })
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

  const remoteSettingsDisabled = !credentials || connection.connecting || loading;
  const remoteDownloadButtonDisabled = remoteSettingsDisabled || fileNameErrorMessage.length > 0;
  const downloadRemotelyButton = buildDownloadRemotelyButton(credentials, connection, remoteDownloadButtonDisabled, onDownloadRemotely);

  return (
    <DownloadPrompt
      fileName={fileName}
      filePath={filePath.join('/')}
      onFileNameChange={onFileNameChange}
      onDownloadLocally={onDownloadLocally}
      onDelete={onDelete}
      filePathElement={filePathElement}
      downloadRemotelyButtonElement={downloadRemotelyButton}
      remoteSettingsDisabled={remoteSettingsDisabled}
      localSettingsDisabled={loading}
      onSubmit={onDownloadRemotely}
      errorMessage={errorMessage}
      fileNameErrorMessage={fileNameErrorMessage}
      />
  )
}

function buildDownloadRemotelyButton(
  credentials: UserCredentials | undefined,
  connection: ConnectionContextType,
  loading: boolean,
  onDownloadRemotely: MouseEventHandler<HTMLButtonElement>
) {
  if (!credentials) {
    return <DisabledButton message="Log in first." />
  }
  if (connection.connecting) {
    return <DisabledButton message="Connecting to the server..." icon={<Loader size="small" />} />
  }
  if (connection.failedToConnectReason) {
    return <DisabledButton message={connection.failedToConnectReason} />
  }
  if (connection.connected) {
    return <Button icon={<FeatherCloud />} onClick={onDownloadRemotely} disabled={loading}>Download Remotely</Button>;
  }

  // shouldn't happen
  return <Button icon={<FeatherCloud />} disabled={true}>Download Remotely</Button>;
}

const DisabledButton = ({ message, icon = <FeatherCloud /> }: { message: string, icon?: React.ReactNode }) => (
  <Tooltip.Provider>
    <Tooltip.Root delayDuration={0}>
      <Tooltip.Trigger>
        <Button icon={icon} disabled={true}>Download Remotely</Button>
      </Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content
          side="top"
          align="center"
          sideOffset={4}
          asChild={true}
        >
          <MessageTooltip>{message}</MessageTooltip>
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  </Tooltip.Provider>
)

function buildDefaultFileName(pendingDownload: PendingDownload) {
  if (pendingDownload.filename && pendingDownload.filename.length > 0) {
    return pendingDownload.filename;
  }

  const url = pendingDownload.finalUrl || pendingDownload.url;
  if (url) {
    return resolveFileNameFromURL(url);
  }

  return '';
}