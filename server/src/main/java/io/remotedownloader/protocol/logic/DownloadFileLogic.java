package io.remotedownloader.protocol.logic;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.protocol.StringMessage;

public class DownloadFileLogic {
    private final DownloadManagerDao downloadManagerDao;

    public DownloadFileLogic(Holder holder) {
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    public StringMessage handleRequest(ChannelHandlerContext ctx, StringMessage msg) {
        DownloadUrlRequestDTO req = msg.parseJson(DownloadUrlRequestDTO.class);
        downloadManagerDao.download(ctx, msg, req.url(), req.fileName(), req.path());
        return null;
    }
}
