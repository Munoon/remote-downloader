package io.remotedownloader.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.protocol.dto.DownloadUrlRequestDTO;
import io.remotedownloader.protocol.dto.Error;
import io.remotedownloader.protocol.dto.InfoMessage;
import io.remotedownloader.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MessageHandler extends SimpleChannelInboundHandler<StringMessage> {
    private static final Logger log = LogManager.getLogger(MessageHandler.class);
    private final String infoMessageJson;
    private final DownloadManagerDao downloadManagerDao;

    public MessageHandler(Holder holder) {
        super(StringMessage.class, false);
        this.infoMessageJson = JsonUtil.writeValueAsString(new InfoMessage("1.0"));
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, StringMessage msg) {
        try {
            StringMessage response = switch (msg.command()) {
                case ProtocolCommands.DOWNLOAD_URL -> downloadUrl(ctx, msg);
                default -> StringMessage.error(msg, Error.ErrorTypes.UNKNOWN_COMMAND, "Unknown command.");
            };

            if (response != null) {
                ctx.writeAndFlush(response);
            }
        } catch (ErrorException e) {
            ctx.writeAndFlush(StringMessage.json(msg, e.error));
        } catch (Exception e) {
            log.warn("Failed unknown exception while handling client request", e);
            ctx.writeAndFlush(StringMessage.error(msg, Error.ErrorTypes.UNKNOWN, "Unknown error."));
        }
    }

    private StringMessage downloadUrl(ChannelHandlerContext ctx, StringMessage msg) {
        DownloadUrlRequestDTO req = msg.parseJson(DownloadUrlRequestDTO.class);
        downloadManagerDao.download(ctx, msg, req.url(), req.fileName());
        return null;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            StringMessage message = new StringMessage(0, ProtocolCommands.SERVER_HELLO, infoMessageJson);
            ctx.writeAndFlush(message);
        }

        super.userEventTriggered(ctx, evt);
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
