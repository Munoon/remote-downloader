package io.remotedownloader.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.remotedownloader.model.dto.Error;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BaseMessageHandler extends SimpleChannelInboundHandler<StringMessage> {
    private static final Logger log = LogManager.getLogger(BaseMessageHandler.class);

    protected BaseMessageHandler() {
        super(StringMessage.class, false);
    }

    abstract StringMessage handleRequest(ChannelHandlerContext ctx, StringMessage msg);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, StringMessage msg) {
        try {
            StringMessage response = handleRequest(ctx, msg);
            if (response != null) {
                ctx.writeAndFlush(response);
            }
        } catch (ErrorException e) {
            Error error = e.error;
            ctx.writeAndFlush(StringMessage.error(msg, error.type(), error.message()));
        } catch (Exception e) {
            log.warn("Failed unknown exception while handling client request", e);
            ctx.writeAndFlush(StringMessage.error(msg, Error.ErrorTypes.UNKNOWN, "Unknown error."));
        }
    }
}
