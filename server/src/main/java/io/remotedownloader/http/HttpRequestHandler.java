package io.remotedownloader.http;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.net.http.HttpRequest;

public class HttpRequestHandler extends SimpleChannelInboundHandler<HttpRequest> {
    private static final DefaultFullHttpResponse NOT_FOUND_RESPONSE =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);

    protected HttpRequestHandler() {
        super(HttpRequest.class, true);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) {
        ctx.writeAndFlush(NOT_FOUND_RESPONSE)
                .addListener(ChannelFutureListener.CLOSE);
    }
}
