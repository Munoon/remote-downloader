import {useState, useEffect, MouseEventHandler, useContext, ChangeEventHandler, EventHandler} from 'react';
import { FeatherFolderPlus, FeatherCheck } from "@subframe/core";
import * as SubframeUtils from "./ui/utils";
import { arrayEquals, copyAndReplace, validateFileName } from "./util";
import { TreeView } from "./ui";
import { ConnectionContext, DownloadFilePathContext, UserCredentialsContext } from './context';
import { Tooltip } from "@subframe/core";
import { Tooltip as MessageTooltip } from "./ui/components/Tooltip";

type FolderStructure = { type: 'folder', id: string, name: string, children?: FileStructure[], editing: boolean, virtual: boolean }
type FileStructure = FolderStructure | { type: 'file', id: string, name: string };

const FilePathSelector = () => (
  <TreeView>
    <StructureFolder
      structure={{ type: 'folder', name: 'Root', id: 'root', children: undefined, editing: false, virtual: false }}
      prefixPath={[]}
      />
  </TreeView>
);
export default FilePathSelector;

function StructureFolder({ structure, prefixPath } : { structure: FolderStructure, prefixPath: string[] }) {
  const { credentials } = useContext(UserCredentialsContext);
  const { filePath: selectedPath, setFilePath } = useContext(DownloadFilePathContext);
  const { connected, client } = useContext(ConnectionContext);
  const [open, setOpen] = useState(false);
  const [children, setChildren] = useState(structure.children);
  const [childrenLoading, setChildrenLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  
  const folderPath = [...prefixPath, structure.name];
  const isRoot = arrayEquals(folderPath, ['Root']);

  if (isRoot) {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    useEffect(() => {
      if (!credentials) {
        if (children) {
          setChildren(undefined);
        }
        if (childrenLoading) {
          setChildrenLoading(false);
        }
        if (open) {
          setOpen(false);
        }
      }
    }, [credentials]);
  }

  const onClick: MouseEventHandler<HTMLDivElement> = (e) => {
    e.preventDefault();
    if (!connected) {
      return;
    }

    setFilePath(folderPath);
    
    if (!open && !structure.virtual && children === undefined && !childrenLoading && client) {
      setChildrenLoading(true);
      if (errorMessage) {
        setErrorMessage('');
      }

      const url = isRoot ? null : folderPath.slice(1).join('/');
      client.listFolders(url)
        .then(resp => {
          const children = mapFilesToStructure(resp.files);
          setChildren(children);
          setChildrenLoading(false);
          setOpen(true);
        })
        .catch((error: ServerError) => {
          setChildrenLoading(false);
          setErrorMessage('Failed to load children: ' + error.message);
        })
    } else {
      setOpen(!open);
    }
  }

  const onNewFolderClick: MouseEventHandler<HTMLDivElement> = (e) => {
    e.preventDefault();
    e.stopPropagation();

    const newFile: FolderStructure = {
      'type': 'folder',
      'name': '',
      editing: true,
      virtual: true,
      
      // prepend the id with '/', so that it won't match with any real file/folder
      id: '/' + Math.random().toString(16)
    }
    setChildren([newFile, ...(children || [])]);
    setOpen(true);
  }

  const selected = arrayEquals(folderPath, selectedPath);
  const label = (
    <span
      className={SubframeUtils.twClassNames(
        "line-clamp-1 shrink-0 text-body font-body text-default-font",
        { "text-brand-700": selected }
      )}>
      {structure.name}
    </span>
  );

  const rightIcon = (
    <FeatherFolderPlus
      className={SubframeUtils.twClassNames(
        "text-body font-body text-default-font",
        { "text-brand-700": selected }
      )}
      onClick={onNewFolderClick}
      />
  );

  const replaceChildren = (id: string, child: FileStructure) => {
    if (children) {
      const newChildren = copyAndReplace(children, c => c.id === id, child);
      setChildren(newChildren);
    }
  }

  return (
    <TreeView.Folder
      label={label}
      selected={selected}
      open={open}
      onFolderClick={onClick}
      loading={childrenLoading}
      rightIcon={rightIcon}
      errorMessage={errorMessage}
    >
      {children && children.map(file => renderStructure(file, folderPath, replaceChildren))}
    </TreeView.Folder>
  );
}

function EditingStructureFolder({ id, prefixPath, setChildren }: {
  id: string,
  prefixPath: string[],
  setChildren: (id: string, child: FileStructure) => void
}) {
  const { setFilePath } = useContext(DownloadFilePathContext);
  const [folderName, setFolderName] = useState('');
  const [validationMessage, setValidationMessage] = useState('');

  function validateName(name: string): boolean {
    const validationResult = validateFileName(name, 'Folder');
    if (validationResult.validationMessage !== validationMessage) {
      setValidationMessage(validationResult.validationMessage);
    }
    return validationResult.valid;
  }

  const onSubmit: EventHandler<any> = (e) => {
    e.preventDefault();
    
    if (validateName(folderName)) {
      setChildren(id, { type: 'folder', id, name: folderName, editing: false, virtual: true });
      setFilePath([...prefixPath, folderName]);
    }
  };

  const onFileNameChange: ChangeEventHandler<HTMLInputElement> = (e) => {
    e.preventDefault();

    const folderName = e.target.value;
    setFolderName(folderName);
    if (validationMessage) {
      validateName(folderName);
    }
  };

  const label = (
    <input
      autoFocus
      name='fieldName'
      value={folderName}
      onChange={onFileNameChange}
      onKeyDown={e => e.key === 'Enter' && onSubmit(e)}
      className={SubframeUtils.twClassNames(
        "group/b0d608f7 h-full w-full border-none bg-transparent text-body font-body text-default-font outline-none placeholder:text-neutral-400"
      )}
      placeholder='Folder name'
      />
  );

  const rightIcon = (
    <FeatherCheck
      className={SubframeUtils.twClassNames(
        "text-body font-body text-default-font",
        { 'text-error-600': validationMessage.length > 0 }
      )}
      onClick={onSubmit}
      />
  );

  return (    
    <Tooltip.Provider>
      <Tooltip.Root open={validationMessage.length > 0}>
        <Tooltip.Trigger className='w-full' type='button' onClick={e => e.preventDefault()}>
          <TreeView.Folder
            label={label}
            selected={false}
            open={false}
            onFolderClick={() => {}}
            loading={false}
            rightIcon={rightIcon}
            validationError={validationMessage.length > 0}
            />
        </Tooltip.Trigger>
        <Tooltip.Portal>
          <Tooltip.Content
            side="top"
            align="center"
            sideOffset={4}
            asChild={true}
          >
            <MessageTooltip>{validationMessage}</MessageTooltip>
          </Tooltip.Content>
        </Tooltip.Portal>
      </Tooltip.Root>
    </Tooltip.Provider>
  );
}

function renderStructure(structure: FileStructure, prefixPath: string[], setChildren: (id: string, child: FileStructure) => void) {
  if (structure.type === 'file') {
    return <TreeView.Item key={structure.id} label={structure.name} />
  } else if (structure.type === 'folder') {
    if (structure.editing) {
      return <EditingStructureFolder key={structure.id} id={structure.id} setChildren={setChildren} prefixPath={prefixPath} />
    } else {
      return <StructureFolder key={structure.id} structure={structure} prefixPath={prefixPath} />
    }
  }
}

function mapFilesToStructure(files: ListFile[]): FileStructure[] {
  const result: FileStructure[] = [];
  for (const file of files) {
    if (file.folder) {
      result.push({ type: 'folder', name: file.fileName, id: file.fileName, editing: false, virtual: false })
    } else {
      result.push({ type: 'file', name: file.fileName, id: file.fileName })
    }
  }
  return result;
}