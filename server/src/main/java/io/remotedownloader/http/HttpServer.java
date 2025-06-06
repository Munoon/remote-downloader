package io.remotedownloader.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.remotedownloader.Holder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HttpServer {
    private static final Logger log = LogManager.getLogger(HttpServer.class);
    private final EventLoopGroup boosGroup;
    private final EventLoopGroup workerGroup;
    private final Class<? extends ServerChannel> channelClass;
    private final Holder holder;
    private Channel channel;

    public HttpServer(Holder holder) {
        IoHandlerFactory ioHandlerFactory;
        if (Epoll.isAvailable()) {
            ioHandlerFactory = EpollIoHandler.newFactory();
            this.channelClass = EpollServerSocketChannel.class;
        } else {
            ioHandlerFactory = NioIoHandler.newFactory();
            this.channelClass = NioServerSocketChannel.class;
        }

        this.boosGroup = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);
        this.workerGroup = new MultiThreadIoEventLoopGroup(2, ioHandlerFactory);
        this.holder = holder;
    }

    public void start() throws InterruptedException {
        int port = holder.serverProperties.getPort();
        try {
            this.channel = new ServerBootstrap()
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .group(boosGroup, workerGroup)
                    .channel(this.channelClass)
                    .childHandler(new HttpChannelInitializer(holder))
                    .bind(port).sync().channel();
            log.info("HTTP server is listening on port {}", port);
        } catch (Exception e) {
            log.error("Failed to start the server on port {}", port, e);
            throw e;
        }
    }

    public void stop() {
        if (channel != null) {
            channel.closeFuture();
            boosGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
