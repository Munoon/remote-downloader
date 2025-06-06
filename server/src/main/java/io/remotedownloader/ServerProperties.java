package io.remotedownloader;

import java.util.Properties;

public class ServerProperties extends Properties {
    public int getPort() {
        return Integer.parseInt(getProperty("port", "8080"));
    }

    public String getDownloadFolder() {
        return getProperty("download.folder", "./downloads");
    }
}
