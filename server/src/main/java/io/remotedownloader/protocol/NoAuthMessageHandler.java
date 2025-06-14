package io.remotedownloader.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.remotedownloader.Holder;
import io.remotedownloader.dao.SessionDao;
import io.remotedownloader.dao.UserDao;
import io.remotedownloader.model.User;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.LoginRequestDTO;
import io.remotedownloader.protocol.logic.LogicHolder;

public class NoAuthMessageHandler extends BaseMessageHandler {
    private final LogicHolder logicHolder;
    private final UserDao userDao;
    private final SessionDao sessionDao;

    public NoAuthMessageHandler(Holder holder) {
        this.logicHolder = new LogicHolder(holder);
        this.userDao = holder.userDao;
        this.sessionDao = holder.sessionDao;
    }

    @Override
    StringMessage handleRequest(ChannelHandlerContext ctx, StringMessage msg) {
        if (msg.command() == ProtocolCommands.LOGIN) {
            return login(ctx, msg);
        } else {
            return StringMessage.error(msg, Error.ErrorTypes.NOT_AUTHENTICATED,
                    "You have to authenticate first.");
        }
    }

    private StringMessage login(ChannelHandlerContext ctx, StringMessage msg) {
        LoginRequestDTO req = msg.parseJson(LoginRequestDTO.class);

        String username = req.username().toLowerCase();
        User user = userDao.getUserByUsername(username);
        if (user != null && user.encryptedPassword().equals(req.password())) {
            MessageHandler newHandler = new MessageHandler(logicHolder, username);
            ctx.pipeline().replace(this, "MessageHandler", newHandler);

            if (req.subscribeOnDownloadingFilesReport()) {
                sessionDao.addSubscription(ctx, username);
            }

            return StringMessage.ok(msg);
        }

        return StringMessage.error(msg, Error.ErrorTypes.INCORRECT_CREDENTIALS, "Incorrect username or password.");
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
