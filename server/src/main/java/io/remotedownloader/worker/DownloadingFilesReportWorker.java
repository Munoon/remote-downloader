package io.remotedownloader.worker;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.dao.SessionDao;
import io.remotedownloader.model.DownloadingFile;
import io.remotedownloader.model.DownloadingFileStatus;
import io.remotedownloader.model.DownloadingFilesReportSubscription;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.FilesHistoryReportDTO;
import io.remotedownloader.protocol.ProtocolCommands;
import io.remotedownloader.protocol.StringMessage;

import java.util.ArrayList;
import java.util.List;

public class DownloadingFilesReportWorker implements Runnable {
    private final FilesStorageDao filesStorageDao;
    private final SessionDao sessionDao;
    private long lastReported = System.currentTimeMillis();

    public DownloadingFilesReportWorker(Holder holder) {
        this.filesStorageDao = holder.filesStorageDao;
        this.sessionDao = holder.sessionDao;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (DownloadingFilesReportSubscription subscription : sessionDao.getDownloadingFilesReportSubscriptions()) {
            ChannelHandlerContext ctx = subscription.ctx();
            if (!ctx.channel().isWritable()) {
                continue;
            }

            DownloadingFile[] files = filesStorageDao.getUserFiles(subscription.username());
            List<DownloadFileDTO> filteredFiles = new ArrayList<>(files.length);
            for (DownloadingFile file : files) {
                if (file.status == DownloadingFileStatus.DOWNLOADING || file.updatedAt >= lastReported) {
                    filteredFiles.add(new DownloadFileDTO(file));
                }
            }

            if (!filteredFiles.isEmpty()) {
                FilesHistoryReportDTO report = new FilesHistoryReportDTO(filteredFiles);
                ctx.writeAndFlush(StringMessage.json(0, ProtocolCommands.FILES_HISTORY_REPORT, report));
            }
        }

        this.lastReported = now;
    }
}
