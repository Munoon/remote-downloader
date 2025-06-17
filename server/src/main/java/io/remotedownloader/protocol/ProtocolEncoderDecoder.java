package io.remotedownloader.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;

public class ProtocolEncoderDecoder extends ChannelDuplexHandler {
    private static final Logger log = LogManager.getLogger(ProtocolEncoderDecoder.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof BinaryWebSocketFrame frame) {
            try {
                StringMessage stringMessage = decode(frame);
                ctx.fireChannelRead(stringMessage);
            } catch (Exception e) {
                log.warn("Failed to decode the message", e);
            } finally {
                frame.release();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private StringMessage decode(BinaryWebSocketFrame msg) {
        ByteBuf in = msg.content();

        int id = in.readInt();
        short command = in.readShort();
        String data = in.isReadable() ? in.toString(StandardCharsets.UTF_8) : null;
        return new StringMessage(id, command, data);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof StringMessage stringMessage) {
            BinaryWebSocketFrame frame = encode(ctx, stringMessage);
            ctx.write(frame, promise);
        } else {
            ctx.write(msg, promise);
        }
    }

    private BinaryWebSocketFrame encode(ChannelHandlerContext ctx, StringMessage msg) {
        String data = msg.data();
        byte[] dataBytes = data != null && !data.isEmpty() ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];

        ByteBuf buf = ctx.alloc().ioBuffer(4 + 2 + dataBytes.length);
        buf.writeInt(msg.id());
        buf.writeShort(msg.command());
        buf.writeBytes(dataBytes);

        return new BinaryWebSocketFrame(buf);
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
