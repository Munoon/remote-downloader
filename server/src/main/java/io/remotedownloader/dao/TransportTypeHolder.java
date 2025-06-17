package io.remotedownloader.dao;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.remotedownloader.ServerProperties;

public class TransportTypeHolder {
    public final EventLoopGroup boosGroup;
    public final EventLoopGroup workerGroup;
    public final Class<? extends ServerChannel> channelClass;

    public TransportTypeHolder(ServerProperties properties) {
        IoHandlerFactory ioHandlerFactory;
        if (Epoll.isAvailable()) {
            ioHandlerFactory = EpollIoHandler.newFactory();
            this.channelClass = EpollServerSocketChannel.class;
        } else {
            ioHandlerFactory = NioIoHandler.newFactory();
            this.channelClass = NioServerSocketChannel.class;
        }

        this.boosGroup = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);
        this.workerGroup = new MultiThreadIoEventLoopGroup(properties.getThreadsCount(), ioHandlerFactory);
    }

    public void close() {
        boosGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
