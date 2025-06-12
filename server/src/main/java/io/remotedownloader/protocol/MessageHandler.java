package io.remotedownloader.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.DownloadManagerDao;
import io.remotedownloader.protocol.dto.DownloadUrlRequestDTO;
import io.remotedownloader.protocol.dto.Error;
import io.remotedownloader.protocol.dto.Error.ErrorTypes;
import io.remotedownloader.protocol.dto.FileDTO;
import io.remotedownloader.protocol.dto.FileIdRequestDTO;
import io.remotedownloader.protocol.dto.FileStatus;
import io.remotedownloader.protocol.dto.ListFoldersRequestDTO;
import io.remotedownloader.protocol.dto.ListFoldersResponseDTO;
import io.remotedownloader.protocol.dto.LoginRequestDTO;
import io.remotedownloader.protocol.dto.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class MessageHandler extends SimpleChannelInboundHandler<StringMessage> {
    private static final Logger log = LogManager.getLogger(MessageHandler.class);
    private final DownloadManagerDao downloadManagerDao;
    private volatile FileDTO[] files = {
            new FileDTO("1", "example1.png", FileStatus.DOWNLOADING, 10000000, 2500000, 10543),
            new FileDTO("2", "example2.png", FileStatus.DOWNLOADED, 100, 100, 2),
            new FileDTO("3", "example3.png", FileStatus.PAUSED, 10000000, 2500, 10543)
    };

    public MessageHandler(Holder holder) {
        super(StringMessage.class, false);
        this.downloadManagerDao = holder.downloadManagerDao;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, StringMessage msg) {
        try {
            StringMessage response = switch (msg.command()) {
                case ProtocolCommands.LOGIN -> login(msg);
                case ProtocolCommands.DOWNLOAD_URL -> downloadUrl(ctx, msg);
                case ProtocolCommands.GET_FILES_HISTORY -> getFilesHistory(msg);
                case ProtocolCommands.STOP_DOWNLOADING -> stopDownloading(msg);
                case ProtocolCommands.DELETE_FILE -> deleteFile(msg);
                case ProtocolCommands.RESUME_DOWNLOADING -> resumeDownloading(msg);
                case ProtocolCommands.LIST_FOLDERS -> listFolders(msg);
                default -> StringMessage.error(msg, ErrorTypes.UNKNOWN_COMMAND, "Unknown command.");
            };

            if (response != null) {
                ctx.writeAndFlush(response);
            }
        } catch (ErrorException e) {
            ctx.writeAndFlush(StringMessage.json(msg, e.error));
        } catch (Exception e) {
            log.warn("Failed unknown exception while handling client request", e);
            ctx.writeAndFlush(StringMessage.error(msg, ErrorTypes.UNKNOWN, "Unknown error."));
        }
    }

    private StringMessage login(StringMessage msg) {
        LoginRequestDTO req = msg.parseJson(LoginRequestDTO.class);
        return "admin".equals(req.username())
                ? StringMessage.ok(msg)
                : StringMessage.error(msg, ErrorTypes.INCORRECT_CREDENTIALS, "Incorrect username or password.");
    }

    private StringMessage downloadUrl(ChannelHandlerContext ctx, StringMessage msg) {
        DownloadUrlRequestDTO req = msg.parseJson(DownloadUrlRequestDTO.class);
        downloadManagerDao.download(ctx, msg, req.url(), req.fileName(), req.path());
        return null;
    }

    private StringMessage getFilesHistory(StringMessage req) {
        return StringMessage.json(req, new Page<>(files, files.length));
    }

    private StringMessage stopDownloading(StringMessage msg) {
        String fileId = msg.parseJson(FileIdRequestDTO.class).fileId();

        FileDTO[] filesCopy = Arrays.copyOf(files, files.length);
        for (int i = 0; i < filesCopy.length; i++) {
            FileDTO file = filesCopy[i];
            if (file.id().equals(fileId)) {
                filesCopy[i] = new FileDTO(file.id(), file.name(), FileStatus.PAUSED, file.totalBytes(), file.downloadedBytes(), file.speedBytesPerSecond());
                this.files = filesCopy;
                return StringMessage.json(msg, filesCopy[i]);
            }
        }

        return StringMessage.error(msg, ErrorTypes.NOT_FOUND, "File is not found.");
    }

    private StringMessage deleteFile(StringMessage msg) {
        String fileId = msg.parseJson(FileIdRequestDTO.class).fileId();

        for (int i = 0; i < files.length; i++) {
            FileDTO file = files[i];
            if (file.id().equals(fileId)) {
                FileDTO[] newFiles = Arrays.copyOf(files, files.length - 1);
                System.arraycopy(files, 0, newFiles, 0, i);
                System.arraycopy(files, i + 1, newFiles, i, files.length - i - 1);
                this.files = newFiles;
                break;
            }
        }

        return StringMessage.ok(msg);
    }

    private StringMessage resumeDownloading(StringMessage msg) {
        String fileId = msg.parseJson(FileIdRequestDTO.class).fileId();

        FileDTO[] filesCopy = Arrays.copyOf(files, files.length);
        for (int i = 0; i < filesCopy.length; i++) {
            FileDTO file = filesCopy[i];
            if (file.id().equals(fileId)) {
                filesCopy[i] = new FileDTO(file.id(), file.name(), FileStatus.DOWNLOADING, file.totalBytes(), file.downloadedBytes(), file.speedBytesPerSecond());
                this.files = filesCopy;
                return StringMessage.json(msg, filesCopy[i]);
            }
        }

        return StringMessage.error(msg, ErrorTypes.NOT_FOUND, "Files is not found.");
    }

    private StringMessage listFolders(StringMessage msg) {
        ListFoldersRequestDTO req = msg.parseJson(ListFoldersRequestDTO.class);
        ListFoldersResponseDTO response = downloadManagerDao.listFolders(req.path());
        return StringMessage.json(msg, response);
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
