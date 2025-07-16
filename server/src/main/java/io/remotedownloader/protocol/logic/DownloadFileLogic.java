package io.remotedownloader.protocol.logic;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.protocol.BaseMessageHandler;
import io.remotedownloader.protocol.ErrorException;
import io.remotedownloader.protocol.StringMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.uri.Uri;

import java.util.concurrent.CompletableFuture;

public class DownloadFileLogic {
    private static final Logger log = LogManager.getLogger(DownloadFileLogic.class);
    private final DownloadManagerDao downloadManagerDao;

    public DownloadFileLogic(Holder holder) {
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    public StringMessage handleRequest(ChannelHandlerContext ctx, StringMessage msg, String username) {
        DownloadUrlRequestDTO req = msg.parseJsonAndValidate(DownloadUrlRequestDTO.class);
        Uri uri = validateUri(req.url());

        CompletableFuture<Void> future = downloadManagerDao.download(ctx, msg, req, username, uri);
        BaseMessageHandler.handleException(future, ctx, msg);
        return null;
    }

    private static Uri validateUri(String url) {
        Uri uri;
        try {
            uri = Uri.create(url);
        } catch (Exception e) {
            log.debug("Failed to parse URL: '{}'", url, e);
            throw new ErrorException(Error.ErrorTypes.VALIDATION, "Failed to parse URL.");
        }

        String scheme = uri.getScheme();
        if (!Uri.HTTP.equals(scheme) && !Uri.HTTPS.equals(scheme)) {
            throw new ErrorException(Error.ErrorTypes.VALIDATION, "Unsupported URL schema.");
        }

        return uri;
    }
}
