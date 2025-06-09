package io.remotedownloader.protocol;

public class ProtocolCommands {
    public static final short SERVER_HELLO = 1;
    public static final short ERROR = 2;
    public static final short OK = 3;
    public static final short DOWNLOAD_URL = 4;
    public static final short GET_FILES_HISTORY = 5;
    public static final short DELETE_FILE = 6;
    public static final short STOP_DOWNLOADING = 7;
    public static final short RESUME_DOWNLOADING = 8;
    public static final short LIST_FOLDERS = 9;
}
