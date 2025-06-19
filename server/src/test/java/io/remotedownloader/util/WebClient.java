package io.remotedownloader.util;

import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.ReferenceCountUtil;
import io.remotedownloader.BaseTest;
import io.remotedownloader.model.User;
import io.remotedownloader.model.dto.DownloadFileDTO;
import io.remotedownloader.model.dto.DownloadUrlRequestDTO;
import io.remotedownloader.model.dto.Error;
import io.remotedownloader.model.dto.FileIdRequestDTO;
import io.remotedownloader.model.dto.FilesHistoryReportDTO;
import io.remotedownloader.model.dto.GetFilesHistoryRequestDTO;
import io.remotedownloader.model.dto.ListFoldersRequestDTO;
import io.remotedownloader.model.dto.ListFoldersResponseDTO;
import io.remotedownloader.model.dto.LoginRequestDTO;
import io.remotedownloader.model.dto.Page;
import io.remotedownloader.protocol.ProtocolCommands;
import io.remotedownloader.protocol.ProtocolEncoderDecoder;
import io.remotedownloader.protocol.StringMessage;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.URI;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class WebClient {
    private static final Queue<WebClient> clients = new ConcurrentLinkedQueue<>();
    private final Channel channel;
    private final MultiThreadIoEventLoopGroup eventExecutors;
    private final MessageHandler messageHandler;
    private int commandId;

    public WebClient() throws InterruptedException {
        this.eventExecutors = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        this.messageHandler = spy(new MessageHandler());

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                URI.create("ws://127.0.0.1:18080/websocket"),
                WebSocketVersion.V13,
                null,
                true,
                new DefaultHttpHeaders()
        );
        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker);

        this.channel = new Bootstrap()
                .group(eventExecutors)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1_000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(8192),
                                handler,
                                new ProtocolEncoderDecoder(),
                                messageHandler);
                    }
                })
                .connect("127.0.0.1", 18080)
                .sync()
                .channel();
        handler.handshakeFuture().sync();
    }

    public static WebClient loggedAdminWebClient() throws InterruptedException {
        WebClient webClient = new WebClient();

        User adminUser = BaseTest.adminUser;
        webClient.login(new LoginRequestDTO(adminUser.username(), adminUser.encryptedPassword(), true))
                .verifyOk(1);

        webClient.reset();
        return webClient;
    }

    public WebClient login(LoginRequestDTO req) {
        return send(ProtocolCommands.LOGIN, req);
    }

    public WebClient downloadFile(String url, String fileName, String path) {
        return send(ProtocolCommands.DOWNLOAD_URL, new DownloadUrlRequestDTO(url, fileName, path));
    }

    public DownloadFileDTO parseDownloadFile(int id) {
        return getMessage(id).parseJson(DownloadFileDTO.class);
    }

    public WebClient stopDownloading(String fileId) {
        return send(ProtocolCommands.STOP_DOWNLOADING, new FileIdRequestDTO(fileId));
    }

    public WebClient resumeDownloading(String fileId) {
        return send(ProtocolCommands.RESUME_DOWNLOADING, new FileIdRequestDTO(fileId));
    }

    public WebClient deleteFile(String fileId) {
        return send(ProtocolCommands.DELETE_FILE, new FileIdRequestDTO(fileId));
    }

    public FilesHistoryReportDTO parseFilesHistoryReport(int id) {
        return getMessage(id).parseJson(FilesHistoryReportDTO.class);
    }

    public WebClient listFolders(String path) {
        return send(ProtocolCommands.LIST_FOLDERS, new ListFoldersRequestDTO(path));
    }

    public ListFoldersResponseDTO parseListFoldersResponse(int id) {
        return getMessage(id).parseJson(ListFoldersResponseDTO.class);
    }

    public WebClient getFiles(int page, int size) {
        return send(ProtocolCommands.GET_FILES_HISTORY, new GetFilesHistoryRequestDTO(page, size));
    }

    public Page<DownloadFileDTO> parseFilesPage(int id) {
        return getMessage(id).parseJson(new TypeReference<>() {});
    }

    private WebClient send(short command, Object data) {
        StringMessage msg = new StringMessage(++commandId, command, JsonUtil.writeValueAsString(data));
        channel.writeAndFlush(msg);
        return this;
    }

    public void verifyError(int id, Error.ErrorTypes type, String message) {
        ArgumentCaptor<StringMessage> messageCaptor = ArgumentCaptor.forClass(StringMessage.class);
        verify(messageHandler, timeout(500).times(1))
                .message(argThat(m -> m.id() == id && m.command() == ProtocolCommands.ERROR));
        verify(messageHandler, atLeastOnce()).message(messageCaptor.capture());

        for (StringMessage msg : messageCaptor.getAllValues()) {
            if (msg.id() == id && msg.command() == ProtocolCommands.ERROR) {
                assertEquals(new Error(type, message), msg.parseJson(Error.class));
                return;
            }
        }
        throw new RuntimeException("Message is not found.");
    }

    private StringMessage getMessage(int id) {
        ArgumentCaptor<StringMessage> messageCaptor = ArgumentCaptor.forClass(StringMessage.class);
        verify(messageHandler, timeout(500).times(1))
                .message(argThat(m -> m.id() == id && m.command() != ProtocolCommands.ERROR));
        verify(messageHandler, atLeastOnce()).message(messageCaptor.capture());

        for (StringMessage msg : messageCaptor.getAllValues()) {
            if (msg.id() == id && msg.command() != ProtocolCommands.ERROR) {
                return msg;
            }
        }
        throw new RuntimeException("Message is not found.");
    }

    public WebClient verifyOk(int id) {
        verify(messageHandler, timeout(500).times(1))
                .message(argThat(m -> m.id() == id && m.data() == null && m.command() != ProtocolCommands.ERROR));
        return this;
    }

    public void reset() {
        Mockito.clearInvocations(messageHandler);
        commandId = 0;
    }

    private static class WebSocketClientHandler extends ChannelInboundHandlerAdapter {
        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        public ChannelFuture handshakeFuture() {
            return handshakeFuture;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            handshakeFuture = ctx.newPromise();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                Channel channel = ctx.channel();
                switch (msg) {
                    case FullHttpResponse fullHttpResponse -> {
                        if (!handshaker.isHandshakeComplete()) {
                            try {
                                handshaker.finishHandshake(channel, fullHttpResponse);
                                handshakeFuture.setSuccess();
                            } catch (WebSocketHandshakeException e) {
                                handshakeFuture.setFailure(e);
                            }
                        }
                    }
                    case CloseWebSocketFrame ignored -> channel.close();
                    case BinaryWebSocketFrame frame -> ctx.fireChannelRead(frame);
                    default -> System.out.println("Handled unknown message in WebSocketClientHandler: " + msg);
                }
            } finally {
                if (!(msg instanceof BinaryWebSocketFrame)) {
                    ReferenceCountUtil.release(msg);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            if (!handshakeFuture.isDone()) {
                handshakeFuture.setFailure(cause);
            }
            ctx.close();
        }
    }

    private static class MessageHandler extends SimpleChannelInboundHandler<StringMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, StringMessage msg) throws Exception {
            message(msg);
        }

        public void message(StringMessage msg) {
        }
    }

    public void close() {
        this.channel.close();
        this.eventExecutors.close();
    }

    public static void closeAllClients() {
        WebClient client;
        while ((client = clients.poll()) != null) {
            client.close();
        }
    }
}
