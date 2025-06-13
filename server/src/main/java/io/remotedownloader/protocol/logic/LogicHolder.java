package io.remotedownloader.protocol.logic;

import io.remotedownloader.Holder;

public class LogicHolder {
    public final DownloadFileLogic downloadFileLogic;
    public final GetFilesHistoryLogic getFilesHistoryLogic;
    public final StopDownloadingLogic stopDownloadingLogic;
    public final DeleteFileLogic deleteFileLogic;
    public final ResumeDownloadLogic resumeDownloadLogic;
    public final ListFoldersLogic listFoldersLogic;

    public LogicHolder(Holder holder) {
        this.downloadFileLogic = new DownloadFileLogic(holder);
        this.getFilesHistoryLogic = new GetFilesHistoryLogic(holder);
        this.stopDownloadingLogic = new StopDownloadingLogic(holder);
        this.deleteFileLogic = new DeleteFileLogic(holder);
        this.resumeDownloadLogic = new ResumeDownloadLogic(holder);
        this.listFoldersLogic = new ListFoldersLogic(holder);
    }
}
