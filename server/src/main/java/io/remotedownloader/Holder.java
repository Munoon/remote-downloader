package io.remotedownloader;

import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.dao.SessionDao;
import io.remotedownloader.dao.ThreadPoolsHolder;
import io.remotedownloader.dao.UserDao;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;

public class Holder {
    public final ServerProperties serverProperties;
    public final AsyncHttpClient asyncHttpClient;
    public final DownloadManagerDao downloadManagerDao;
    public final UserDao userDao;
    public final FilesStorageDao filesStorageDao;
    public final SessionDao sessionDao;
    public final ThreadPoolsHolder threadPoolsHolder;

    public Holder() {
        this.serverProperties = new ServerProperties();
        this.asyncHttpClient = new DefaultAsyncHttpClient();
        this.filesStorageDao = new FilesStorageDao();
        this.downloadManagerDao = new DownloadManagerDao(serverProperties, asyncHttpClient, filesStorageDao);
        this.userDao = new UserDao();
        this.sessionDao = new SessionDao();
        this.threadPoolsHolder = new ThreadPoolsHolder();
    }
}