package io.remotedownloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

public class ServerProperties extends Properties {
    private static final Logger log = LogManager.getLogger(ServerProperties.class);

    public int getPort() {
        return getIntProperty("port", 8080);
    }

    public String getDownloadFolder() {
        return getProperty("download.folder", "./downloads");
    }

    public String getStorageFile() {
        return getProperty("storage.file", "./storage");
    }

    public boolean getFollowRedirect() {
        return getBooleanProperty("follow.redirect", false);
    }

    public int getMaxRedirects() {
        return getIntProperty("download.request.max.redirects", 10);
    }

    public int getReadTimeoutSeconds() {
        return getIntProperty("download.request.read.timeout.seconds", 10);
    }

    public int getMaxRequestRetry() {
        return getIntProperty("download.request.max.retries", 5);
    }

    public int getThreadsCount() {
        return getIntProperty("threads.count", 2);
    }

    public int getFileMapSize() {
        return getIntProperty("file.map.size", 64 * 1024 * 1024); // 64MB
    }

    public int getFileCommitSize() {
        return getIntProperty("file.commit.size", 1024 * 1024 * 1024); // 1GB
    }

    private int getIntProperty(String key, int defaultValue) {
        String strValue = getProperty(key);
        if (strValue != null) {
            try {
                return Integer.parseInt(strValue);
            } catch (Exception e) {
                log.warn("Failed to parse integer for '{}' property. Falling back to the default", key, e);
            }
        }
        return defaultValue;
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String strValue = getProperty(key);
        return strValue == null ? defaultValue : strValue.equals("true");
    }
}
