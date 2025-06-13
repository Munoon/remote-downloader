package io.remotedownloader.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.remotedownloader.Holder;
import io.remotedownloader.protocol.NoAuthMessageHandler;
import io.remotedownloader.protocol.ProtocolEncoderDecoder;

public class HttpChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final WebSocketServerProtocolConfig webSocketConfig;
    private final NoAuthMessageHandler noAuthMessageHandler;
    private final ProtocolEncoderDecoder protocolEncoderDecoder;
    private final HttpRequestHandler httpRequestHandler;

    public HttpChannelInitializer(Holder holder) {
        WebSocketDecoderConfig decoderConfig = WebSocketDecoderConfig.newBuilder()
                .maxFramePayloadLength(64 * 1024)
                .withUTF8Validator(false) // we use only binary frames, no need in utf8 validator
                .build();

        this.webSocketConfig = WebSocketServerProtocolConfig
                .newBuilder()
                .websocketPath("/websocket")
                .decoderConfig(decoderConfig)
                .build();

        this.noAuthMessageHandler = new NoAuthMessageHandler(holder);
        this.protocolEncoderDecoder = new ProtocolEncoderDecoder();
        this.httpRequestHandler = new HttpRequestHandler();
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast("HttpServerCodec", new HttpServerCodec())
                .addLast("WebSocketServerProtocolHandler", new WebSocketServerProtocolHandler(webSocketConfig))
                .addLast("ProtocolEncoderDecoder", protocolEncoderDecoder)
                .addLast("MessageHandler", noAuthMessageHandler)
                .addLast("HttpRequestHandler", httpRequestHandler);
    }
}
