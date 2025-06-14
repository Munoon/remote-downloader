package io.remotedownloader.dao;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.model.DownloadingFilesReportSubscription;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionDao {
    public final Map<Channel, DownloadingFilesReportSubscription> filesSubscriptions = new ConcurrentHashMap<>();

    public void addSubscription(ChannelHandlerContext ctx, String username) {
        Channel channel = ctx.channel();
        filesSubscriptions.put(channel, new DownloadingFilesReportSubscription(username, ctx));
        channel.closeFuture()
                .addListener(f -> filesSubscriptions.remove(channel));
    }

    public Collection<DownloadingFilesReportSubscription> getDownloadingFilesReportSubscriptions() {
        return filesSubscriptions.values();
    }
}
