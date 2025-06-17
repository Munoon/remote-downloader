package io.remotedownloader;

import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.dao.FilesStorageDao;
import io.remotedownloader.dao.SessionDao;
import io.remotedownloader.dao.StorageDao;
import io.remotedownloader.dao.ThreadPoolsHolder;
import io.remotedownloader.dao.TransportTypeHolder;
import io.remotedownloader.dao.UserDao;

public class Holder {
    public final ServerProperties serverProperties;
    public final TransportTypeHolder transportTypeHolder;
    public final ThreadPoolsHolder threadPoolsHolder;
    public final StorageDao storageDao;
    public final FilesStorageDao filesStorageDao;
    public final DownloadManagerDao downloadManagerDao;
    public final UserDao userDao;
    public final SessionDao sessionDao;

    public Holder() {
        this.serverProperties = new ServerProperties();
        this.transportTypeHolder = new TransportTypeHolder(serverProperties);
        this.threadPoolsHolder = new ThreadPoolsHolder();
        this.storageDao = new StorageDao(serverProperties, threadPoolsHolder);
        this.filesStorageDao = new FilesStorageDao(storageDao);
        this.downloadManagerDao = new DownloadManagerDao(
                serverProperties, transportTypeHolder, filesStorageDao, threadPoolsHolder);
        this.userDao = new UserDao(storageDao);
        this.sessionDao = new SessionDao();
    }
}