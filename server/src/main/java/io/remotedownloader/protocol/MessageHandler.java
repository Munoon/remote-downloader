package io.remotedownloader.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.model.dto.Error.ErrorTypes;
import io.remotedownloader.protocol.logic.LogicHolder;

public class MessageHandler extends BaseMessageHandler {
    private final LogicHolder logicHolder;
    private final String username;

    public MessageHandler(LogicHolder logicHolder, String username) {
        this.logicHolder = logicHolder;
        this.username = username;
    }

    @Override
    StringMessage handleRequest(ChannelHandlerContext ctx, StringMessage msg) {
        return switch (msg.command()) {
            case ProtocolCommands.DOWNLOAD_URL -> logicHolder.downloadFileLogic.handleRequest(ctx, msg, username);
            case ProtocolCommands.GET_FILES_HISTORY -> logicHolder.getFilesHistoryLogic.handleRequest(msg, username);
            case ProtocolCommands.STOP_DOWNLOADING -> logicHolder.stopDownloadingLogic.handleRequest(msg, username);
            case ProtocolCommands.DELETE_FILE -> logicHolder.deleteFileLogic.handleRequest(msg, username);
            case ProtocolCommands.RESUME_DOWNLOADING -> logicHolder.resumeDownloadLogic.handleRequest(ctx, msg, username);
            case ProtocolCommands.LIST_FOLDERS -> logicHolder.listFoldersLogic.handleRequest(ctx, msg);

            case ProtocolCommands.LOGIN -> StringMessage.error(
                    msg, ErrorTypes.ALREADY_AUTHENTICATED, "You are already authenticated.");
            default -> StringMessage.error(msg, ErrorTypes.UNKNOWN_COMMAND, "Unknown command.");
        };
    }
}
