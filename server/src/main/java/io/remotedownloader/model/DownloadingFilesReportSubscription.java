package io.remotedownloader.model;

import io.netty.channel.ChannelHandlerContext;

public record DownloadingFilesReportSubscription(
        String username,
        ChannelHandlerContext ctx
) {
}
