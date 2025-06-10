import { useContext, useEffect, useState, createContext, MouseEventHandler } from "react";
import { DownloadPrompt, TreeView } from "./ui";
import { arrayEquals, resolveFileNameFromURL } from "./util";
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

export const FilesStructureContext = createContext<{
  structure: FileStructure[],
  setStructure: (structure: FileStructure[]) => void
}>({ structure: [], setStructure: () => {} });

function PendingDownload({ pendingDownload, onRefresh }: { pendingDownload: PendingDownload, onRefresh: (downloads: PendingDownload[]) => void }) {
  const url = pendingDownload.finalUrl || pendingDownload.url;
  const [fileName, setFileName] = useState(pendingDownload.filename || (url ? resolveFileNameFromURL(url) : ''));
  const [filePath, setFilePath] = useState<string[]>(['Root']);
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
    
    const path = filePath.length === 1 ? undefined : filePath.slice(1).join('/');
    client.downloadFile(url, fileName, path)
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
      filePath={filePath.length > 0 ? filePath.join('/') : 'Root'}
      onFileNameChange={onFileNameChange}
      onDownloadLocaly={onDownloadLocaly}
      onDownloadRemotely={onDownloadRemotely}
      onDelete={onDelete}
      filePathElement={<FilePathSelector filePath={filePath} pathSetter={setFilePath} />}
      disabled={loading}
      />
  )
}

type FolderStructure = { type: 'folder', name: string, children?: FileStructure[], new: boolean }
type FileStructure = FolderStructure | { type: 'file', name: string };

function FilePathSelector({ filePath, pathSetter }: { filePath: string[], pathSetter: (path: string[]) => void }) {
  const [structure, setStructure] = useState<FileStructure[]>([]);
  useEffect(() => {
    if (structure.length > 0) {
      return;
    }

    client.listFolders(null)
      .then(resp => setStructure(mapFilesToStrucutre(resp.files)));
  }, []);

  const rootFolder: FolderStructure = { type: 'folder', name: 'Root', children: structure, new: false };
  return (
    <FilesStructureContext.Provider value={{ structure, setStructure }}>
      <TreeView>
        {renderStructure(rootFolder, [], filePath, pathSetter)}
      </TreeView>
    </FilesStructureContext.Provider>
  );
}

function mapFilesToStrucutre(files: ListFile[]): FileStructure[] {
  const result: FileStructure[] = [];
  for (let file of files) {
    if (file.folder) {
      result.push({ type: 'folder', name: file.fileName, new: false })
    } else {
      result.push({ type: 'file', name: file.fileName })
    }
  }
  return result;
}

function renderStructure(structure: FileStructure, prefixPath: string[], selectedPath: string[], pathSetter: (path: string[]) => void) {
  if (structure.type === 'file') {
    return <TreeView.Item key={structure.name} label={structure.name} />
  } else if (structure.type === 'folder') {
    return <StructureFolder structure={structure} prefixPath={prefixPath} selectedPath={selectedPath} pathSetter={pathSetter} />
  }
}

function StructureFolder({ structure, prefixPath, selectedPath, pathSetter } : {
  structure: FolderStructure,
  prefixPath: string[],
  selectedPath: string[],
  pathSetter: (path: string[]) => void
}) {
  const [open, setOpen] = useState(false);
  const [childrenLoading, setChildrenLoading] = useState(false);
  const [folderName, setFolderName] = useState(structure.name);
  const { structure: fullStructure, setStructure: setFullStructure } = useContext(FilesStructureContext);
  const folderPath = [...prefixPath, structure.name];

  const onClick: MouseEventHandler<HTMLDivElement> = (e) => {
    e.preventDefault();
    pathSetter(folderPath);
    
    if (!open && structure.children === undefined && !childrenLoading) {
      setChildrenLoading(true);

      const actualFolderPath = folderPath.slice(1);
      client.listFolders(actualFolderPath.join('/'))
        .then(resp => {
          const children = mapFilesToStrucutre(resp.files);
          const updatedFullStructure = replaceChildren(fullStructure, actualFolderPath, children);
          setFullStructure(updatedFullStructure);
          setChildrenLoading(false);
        });
    }

    setOpen(!open);
  }

  const onNewFolderClick: MouseEventHandler<HTMLDivElement> = (e) => {
    e.preventDefault();
    
    const actualFolderPath = folderPath.slice(1);
    const updatedFullStructure = addChildren(fullStructure, actualFolderPath, { 'type': 'folder', 'name': '', new: true })
    setFullStructure(updatedFullStructure);

    setOpen(true);
  }

  return (
    <TreeView.Folder
      key={structure.name}
      label={folderName}
      selected={arrayEquals(folderPath, selectedPath)}
      open={open}
      onFolderClick={onClick}
      loading={childrenLoading}
      onNewFolderClick={onNewFolderClick}
    >
      {structure.children && structure.children.map(file => renderStructure(file, folderPath, selectedPath, pathSetter))}
    </TreeView.Folder>
  );
}

function replaceChildren(structure: FileStructure[], path: string[], children: FileStructure[]): FileStructure[] {
  const folderName = path.shift();
  const index = structure.findIndex(s => s.name === folderName);
  if (index === -1) {
    return structure;
  }

  const folder = structure[index];
  if (folder.type !== 'folder') {
    return structure;
  }

  const structureCopy = [...structure];
  if (path.length === 0) {
    structureCopy[index] = { ...folder, children };
  } else if (folder.children && folder.children.length > 0) {
    structureCopy[index] = { ...folder, children: replaceChildren(folder.children, path, children) };
  }

  return structureCopy;
}

function addChildren(structure: FileStructure[], path: string[], children: FileStructure): FileStructure[] {
  const folderName = path.shift();
  const index = structure.findIndex(s => s.name === folderName);
  if (index === -1) {
    return structure;
  }

  const folder = structure[index];
  if (folder.type !== 'folder') {
    return structure;
  }

  const structureCopy = [...structure];
  if (path.length === 0) {
    structureCopy[index] = { ...folder, children: [children, ...(folder.children || [])] };
  } else if (folder.children && folder.children.length > 0) {
    structureCopy[index] = { ...folder, children: addChildren(folder.children, path, children) };
  }

  return structureCopy;
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