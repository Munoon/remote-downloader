import { useState, useEffect, createContext, MouseEventHandler, useContext } from 'react';
import { arrayEquals, resolveFileNameFromURL } from "./util";
import { TreeView } from "./ui";
import client from './api/client';
import { DownloadFilePathContext } from './context';

type FolderStructure = { type: 'folder', name: string, children?: FileStructure[] }
type FileStructure = FolderStructure | { type: 'file', name: string };

export default function FilePathSelector() {
  const [structure, setStructure] = useState<FileStructure[] | null>(null);
  useEffect(() => {
    client.listFolders(null)
      .then(resp => setStructure(mapFilesToStrucutre(resp.files)));
  }, []);
 
  return (
    <TreeView>
      <StructureFolder
        key={structure ? 1 : 0} // hack: without this key react wouldn't re-render component after fetching the structure
        structure={{ type: 'folder', name: 'Root', children: structure || [] }}
        prefixPath={[]}
        />
    </TreeView>
  )
}

function StructureFolder({ structure, prefixPath } : { structure: FolderStructure, prefixPath: string[] }) {
  const [open, setOpen] = useState(false);
  const [children, setChildren] = useState(structure.children);
  const [childrenLoading, setChildrenLoading] = useState(false);
  const { filePath: selectedPath, setFilePath } = useContext(DownloadFilePathContext);
  const folderPath = [...prefixPath, structure.name];

  const onClick: MouseEventHandler<HTMLDivElement> = (e) => {
    e.preventDefault();
    setFilePath(folderPath);
    
    if (!open && children === undefined && !childrenLoading) {
      setChildrenLoading(true);

      client.listFolders(folderPath.slice(1).join('/'))
        .then(resp => {
          const children = mapFilesToStrucutre(resp.files);
          setChildren(children);
          setChildrenLoading(false);
          setOpen(true);
        });
    } else {
      setOpen(!open);
    }
  }

  const onNewFolderClick: MouseEventHandler<HTMLDivElement> = (e) => {
    e.preventDefault();
    setChildren([{ 'type': 'folder', 'name': 'new folder' }, ...(children || [])]);
    setOpen(true);
  }

  return (
    <TreeView.Folder
      key={structure.name}
      label={structure.name}
      selected={arrayEquals(folderPath, selectedPath)}
      open={open}
      onFolderClick={onClick}
      loading={childrenLoading}
      onNewFolderClick={onNewFolderClick}
    >
      {children && children.map(file => renderStructure(file, folderPath))}
    </TreeView.Folder>
  );
}

function renderStructure(structure: FileStructure, prefixPath: string[]) {
  if (structure.type === 'file') {
    return <TreeView.Item key={structure.name} label={structure.name} />
  } else if (structure.type === 'folder') {
    return <StructureFolder key={structure.name} structure={structure} prefixPath={prefixPath} />
  }
}

function mapFilesToStrucutre(files: ListFile[]): FileStructure[] {
  const result: FileStructure[] = [];
  for (let file of files) {
    if (file.folder) {
      result.push({ type: 'folder', name: file.fileName })
    } else {
      result.push({ type: 'file', name: file.fileName })
    }
  }
  return result;
}