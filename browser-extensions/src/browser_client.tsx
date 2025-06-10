declare const chrome: any;
const PENDING_DOWNLOADS_STORAGE_KEY = 'pendingDownloads';

export interface PendingDownload {
  id: number
  url?: string
  finalUrl?: string
  fileSize?: number
  filename?: string
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

export default {
  getPendingDownloads,
  removePendingDownload,
  resumeDownload
}