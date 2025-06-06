package io.remotedownloader;

import io.remotedownloader.dao.DownloadManagerDao;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;

public class Holder {
    public final ServerProperties serverProperties;
    public final AsyncHttpClient asyncHttpClient;
    public final DownloadManagerDao downloadManagerDao;

    public Holder() {
        this.serverProperties = new ServerProperties();
        this.asyncHttpClient = new DefaultAsyncHttpClient();
        this.downloadManagerDao = new DownloadManagerDao(serverProperties, asyncHttpClient);
    }
}