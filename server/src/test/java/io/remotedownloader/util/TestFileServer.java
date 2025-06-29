package io.remotedownloader.util;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.concurrent.ThreadFactory;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class TestFileServer {
    private static final ThreadFactory THREAD_FACTORY = r -> {
        Thread thread = new Thread(r);
        thread.setName("Test-File-Server");
        return thread;
    };

    private final Channel channel;
    private final MultiThreadIoEventLoopGroup eventExecutors;
    private final NettyRequestHandler handler;
    private volatile RequestHandler requestHandler;

    public TestFileServer(RequestHandler requestHandler) {
        this.handler = spy(new NettyRequestHandler());
        this.eventExecutors = new MultiThreadIoEventLoopGroup(1, THREAD_FACTORY, NioIoHandler.newFactory());
        this.requestHandler = requestHandler;
        ChannelFuture cf = new ServerBootstrap()
                .option(ChannelOption.SO_BACKLOG, 1024)
                .group(eventExecutors)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1024 * 1024))
                                .addLast(handler);
                    }
                })
                .bind(18081);
        try {
            this.channel = cf.sync().channel();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public interface RequestHandler {
        void handle(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception;

        static RequestHandler contentRangeResponding(int contentLength, String contentRange) {
            return (ctx, msg) -> {
                DefaultHttpHeaders headers = new DefaultHttpHeaders();
                headers.add(HttpHeaderNames.CONTENT_RANGE, contentRange);
                headers.add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(contentLength));
                DefaultHttpResponse response = new DefaultHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.PARTIAL_CONTENT,
                        headers
                );
                ctx.writeAndFlush(response);
            };
        }
    }

    private class NettyRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            requestHandler.handle(ctx, msg);
        }

        @Override
        public boolean isSharable() {
            return true;
        }
    }

    public void requestHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public void reset() {
        Mockito.clearInvocations(handler);
    }

    public ChannelHandlerContext verifyRequest() throws Exception {
        return verifyRequest(m -> true);
    }

    public ChannelHandlerContext verifyRequest(ArgumentMatcher<FullHttpRequest> matcher) throws Exception {
        ArgumentCaptor<ChannelHandlerContext> ctx = ArgumentCaptor.forClass(ChannelHandlerContext.class);
        verify(handler, timeout(500).times(1)).channelRead0(ctx.capture(), argThat(matcher));
        return ctx.getValue();
    }

    public static TestFileServer simpleFileServer(byte[] content) {
        return new TestFileServer((ctx, msg) -> {
            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            headers.add(HttpHeaderNames.CONTENT_LENGTH, content.length);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(content),
                    headers,
                    new DefaultHttpHeaders()
            );
            ctx.writeAndFlush(response);
        });
    }

    public static TestFileServer fileLengthResponding(int length) {
        return new TestFileServer((ctx, msg) -> {
            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            headers.add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(length));
            DefaultHttpResponse response = new DefaultHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    headers
            );
            ctx.writeAndFlush(response);
        });
    }

    public void close() throws InterruptedException {
        channel.close().sync();
        eventExecutors.close();
    }
}
