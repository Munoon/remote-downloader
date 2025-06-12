declare const chrome: any;
const PENDING_DOWNLOADS_STORAGE_KEY = 'pendingDownloads';
const USER_CREDENTIALS_STORAGE_KEY = 'userCredentials';

export interface PendingDownload {
  id: number
  url?: string
  finalUrl?: string
  fileSize?: number
  filename?: string
}

export interface UserCredentials {
  address: string
  username: string
  passwordEncrypted: string
}

function resumeDownload(pendingDownload: PendingDownload) {
  chrome.downloads.resume(pendingDownload.id);
}

async function removePendingDownload(pendingDownload: PendingDownload): Promise<PendingDownload[]> {
  const id = pendingDownload.id;
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

async function getCredentials(): Promise<UserCredentials | undefined> {
  const credentials = await chrome.storage.local.get(USER_CREDENTIALS_STORAGE_KEY).then((creds?: any) => creds?.userCredentials);
  return credentials && credentials.address && credentials.username && credentials.passwordEncrypted
    ? credentials
    : undefined;
}

function setCredentials(credentials?: UserCredentials) {
  if (credentials) {
    chrome.storage.local.set({ [USER_CREDENTIALS_STORAGE_KEY]: credentials });
  } else {
    chrome.storage.local.remove(USER_CREDENTIALS_STORAGE_KEY);
  }
}

export default {
  getPendingDownloads,
  removePendingDownload,
  resumeDownload,
  getCredentials,
  setCredentials
}