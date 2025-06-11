import React, { useContext, useEffect, useState, createContext, MouseEventHandler } from "react";
import { DownloadPrompt } from "./ui";
import { resolveFileNameFromURL } from "./util";
import client from "./api/client";
import { ConnectionContext, ConnectionContextType, DownloadFilePathContext, HistoryFilesContext, PendingDownloadContext } from "./context";
import browserClient, { PendingDownload } from "./browser_client";
import FilePathSelector from "./FilePathSelector";
import { Button } from "./ui/components/Button";
import { Tooltip as MessageTooltip } from "./ui/components/Tooltip";
import { FeatherCloud, Tooltip } from "@subframe/core";
import { Loader } from "./ui/components/Loader";

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
  const [fileName, setFileName] = useState(buildDefaultFileName(pendingDownload));
  const [filePath, setFilePath] = useState<string[]>(['Root']);
  const [loading, setLoading] = useState(false);
  const historyFilesContext = useContext(HistoryFilesContext);
  const { setPendingDownloads } = useContext(PendingDownloadContext);
  const connection = useContext(ConnectionContext);

  const onFileNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    setFileName(e.target.value);
  }

  const onDownloadLocally = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    browserClient.resumeDownload(pendingDownload);
    browserClient.removePendingDownload(pendingDownload)
      .then(pendingDownloads => setPendingDownloads(pendingDownloads));
  }

  const onDownloadRemotely = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setLoading(true);
    
    const url = pendingDownload.finalUrl || pendingDownload.url;
    if (!url) { // shouldn't happen
      return;
    }

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

  const downloadRemotelyButton = buildDownloadRemotelyButton(connection, onDownloadRemotely);

  return (
    <DownloadPrompt
      fileName={fileName}
      filePath={filePath.join('/')}
      onFileNameChange={onFileNameChange}
      onDownloadLocally={onDownloadLocally}
      onDownloadRemotely={onDownloadRemotely}
      onDelete={onDelete}
      filePathElement={filePathElement}
      downloadRemotelyButtonElement={downloadRemotelyButton}
      remoteSettingsDisabled={!connection.authenticated || connection.connecting || loading}
      localSettingsDisabled={loading}
      />
  )
}

function buildDownloadRemotelyButton(connection: ConnectionContextType, onDownloadRemotely: MouseEventHandler<HTMLButtonElement>) {
  if (!connection.authenticated) {
    return <DisabledButton message="Log in first." />
  }
  if (connection.connecting) {
    return <DisabledButton message="Connecting to the server..." icon={<Loader size="small" />} />
  }
  if (connection.failedToConnectReason) {
    return <DisabledButton message={connection.failedToConnectReason} />
  }
  if (connection.connected) {
    return <Button icon={<FeatherCloud />} onClick={onDownloadRemotely}>Download Remotely</Button>;
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