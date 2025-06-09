chrome.downloads.onCreated.addListener(async (downloadItem) => {
  chrome.downloads.pause(downloadItem.id);
  await chrome.storage.local.set({ pendingDownload: downloadItem });
  chrome.action.openPopup();
});