package io.remotedownloader.protocol.logic;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.model.dto.ListFoldersRequestDTO;
import io.remotedownloader.model.dto.ListFoldersResponseDTO;
import io.remotedownloader.protocol.BaseMessageHandler;
import io.remotedownloader.protocol.StringMessage;

import java.util.concurrent.CompletableFuture;

public class ListFoldersLogic {
    private final DownloadManagerDao downloadManagerDao;

    public ListFoldersLogic(Holder holder) {
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    public StringMessage handleRequest(ChannelHandlerContext ctx, StringMessage msg) {
        ListFoldersRequestDTO req = msg.parseJsonAndValidate(ListFoldersRequestDTO.class);
        CompletableFuture<ListFoldersResponseDTO> completableFuture = downloadManagerDao.listFolders(req.path());
        BaseMessageHandler.respond(completableFuture, ctx, msg);
        return null;
    }
}
