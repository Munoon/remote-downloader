type Page<T> = {
  content: T[]
  totalElements: number
}

interface HistoryFile {
  id: string
  name: string
  status: 'DOWNLOADING' | 'DOWNLOADED' | 'PAUSED' | 'ERROR'
  totalBytes: number
  downloadedBytes: number
  speedBytesPerMS: number
}

interface FilesHistoryReport {
  files: HistoryFile[]
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
  type: 'UNKNOWN' | 'UNKNOWN_COMMAND' | 'FAILED_TO_DOWNLOAD' | 'NOT_FOUND' | 'INCORRECT_CREDENTIALS' | string;
  message: string
}