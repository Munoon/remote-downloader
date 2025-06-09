chrome.downloads.onCreated.addListener(async (downloadItem) => {
  if (!downloadItem.url && !downloadItem.finalUrl) {
    return;
  }

  chrome.downloads.pause(downloadItem.id);

  const data = await chrome.storage.local.get();
  const pendingDownloads = (data && data.pendingDownloads) || [];
  await chrome.storage.local.set({ pendingDownloads: [downloadItem, ...pendingDownloads] });

  chrome.action.openPopup();
});