package io.remotedownloader.protocol.logic;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.protocol.BaseMessageHandler;
import io.remotedownloader.protocol.StringMessage;

import java.util.concurrent.CompletableFuture;

public class DownloadFileLogic {
    private final DownloadManagerDao downloadManagerDao;

    public DownloadFileLogic(Holder holder) {
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    public StringMessage handleRequest(ChannelHandlerContext ctx, StringMessage msg, String username) {
        DownloadUrlRequestDTO req = msg.parseJsonAndValidate(DownloadUrlRequestDTO.class);
        CompletableFuture<Void> future = downloadManagerDao.download(ctx, msg, req, username);
        BaseMessageHandler.handleException(future, ctx, msg);
        return null;
    }
}
