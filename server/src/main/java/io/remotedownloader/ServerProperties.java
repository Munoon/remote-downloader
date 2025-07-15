package io.remotedownloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ServerProperties extends Properties {
    private static final Logger log = LogManager.getLogger(ServerProperties.class);
    private static final String CONFIG_FILE_ARG_PREFIX = "config.file=";

    public ServerProperties() {
        this(new String[0]);
    }

    public ServerProperties(String[] args) {
        loadConfigFile(args);
        loadArgs(args);
    }

    private void loadConfigFile(String[] args) {
        Path configFile = resolveConfigFilePath(args);
        if (configFile != null) {
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                load(inputStream);
            } catch (Exception e) {
                log.error("Failed to load configuration file {}", configFile, e);
            }
        }
    }

    private void loadArgs(String[] args) {
        for (String arg : args) {
            int separator = arg.indexOf('=');
            if (separator != -1) {
                String key = arg.substring(0, separator);
                String value = arg.substring(separator + 1);
                put(key, value);
            }
        }
    }

    private static Path resolveConfigFilePath(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("config.file=")) {
                String configFilePath = arg.substring(CONFIG_FILE_ARG_PREFIX.length());
                return fixConfigFilePath(configFilePath);
            }
        }

        String configFile = System.getenv("CONFIG_FILE");
        if (configFile != null) {
            return fixConfigFilePath(configFile);
        }

        return fixConfigFilePath("server.properties");
    }

    private static Path fixConfigFilePath(String configFilePath) {
        Path path = Path.of(configFilePath);
        if (Files.exists(path)) {
            if (Files.isRegularFile(path)) {
                return path;
            } else if (Files.isDirectory(path)) {
                Path file = path.resolve("server.properties");
                if (Files.isRegularFile(file)) {
                    return file;
                }
            }
        }
        log.warn("Config file '{}' is not found!", configFilePath);
        return null;
    }

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
