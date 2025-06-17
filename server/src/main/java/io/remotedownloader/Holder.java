package io.remotedownloader;

import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.dao.SessionDao;
import io.remotedownloader.dao.StorageDao;
import io.remotedownloader.dao.ThreadPoolsHolder;
import io.remotedownloader.dao.UserDao;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;

public class Holder {
    public final ServerProperties serverProperties;
    public final ThreadPoolsHolder threadPoolsHolder;
    public final StorageDao storageDao;
    public final AsyncHttpClient asyncHttpClient;
    public final FilesStorageDao filesStorageDao;
    public final DownloadManagerDao downloadManagerDao;
    public final UserDao userDao;
    public final SessionDao sessionDao;

    public Holder() {
        this.serverProperties = new ServerProperties();
        this.threadPoolsHolder = new ThreadPoolsHolder();
        this.storageDao = new StorageDao(serverProperties, threadPoolsHolder);
        this.asyncHttpClient = new DefaultAsyncHttpClient();
        this.filesStorageDao = new FilesStorageDao(storageDao);
        this.downloadManagerDao = new DownloadManagerDao(
                serverProperties, asyncHttpClient, filesStorageDao, threadPoolsHolder);
        this.userDao = new UserDao(storageDao);
        this.sessionDao = new SessionDao();
    }
}