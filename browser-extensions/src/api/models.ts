type ServerHello = {
  serverVersion: string
}

type Page<T> = {
  content: T[]
  totalElements: number
}

type HistoryFile = {
  id: string
  name: string
  status: 'DOWNLOADING' | 'DOWNLOADED' | 'PAUSED'
  totalBytes: number
  downloadedBytes: number
  speedBytesPerSecond: number
}

interface ListFile {
  folder: boolean
  fileName: string
}

interface ListFoldersResponse {
  canDownload: boolean
  files: ListFile[]
}

interface ServerError {
  type: 'UNKNOWN' | 'UNKNOWN_COMMAND' | 'FAILED_TO_DOWNLOAD' | 'NOT_FOUND' | string;
  message: string
}