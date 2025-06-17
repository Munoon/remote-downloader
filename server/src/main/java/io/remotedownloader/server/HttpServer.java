package io.remotedownloader.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.remotedownloader.Holder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HttpServer {
    private static final Logger log = LogManager.getLogger(HttpServer.class);
    private final Holder holder;
    private Channel channel;

    public HttpServer(Holder holder) {
        this.holder = holder;
    }

    public void start() throws InterruptedException {
        int port = holder.serverProperties.getPort();
        try {
            this.channel = new ServerBootstrap()
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .group(holder.transportTypeHolder.boosGroup, holder.transportTypeHolder.workerGroup)
                    .channel(holder.transportTypeHolder.channelClass)
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
        }
    }
}
