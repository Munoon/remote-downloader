package io.remotedownloader.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProtocolEncoderDecoder extends MessageToMessageCodec<BinaryWebSocketFrame, StringMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, StringMessage msg, List<Object> out) {
        String data = msg.data();
        byte[] dataBytes = data != null && !data.isEmpty() ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];

        ByteBuf buf = ctx.alloc().ioBuffer(4 + 2 + dataBytes.length);
        buf.writeInt(msg.id());
        buf.writeShort(msg.command());
        buf.writeBytes(dataBytes);

        out.add(new BinaryWebSocketFrame(buf));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BinaryWebSocketFrame msg, List<Object> out) {
        ByteBuf in = msg.content();

        int id = in.readInt();
        short command = in.readShort();
        String data = in.isReadable() ? in.toString(StandardCharsets.UTF_8) : null;
        out.add(new StringMessage(id, command, data));
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
