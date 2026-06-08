package com.demo.bwim.server;


import com.demo.bwim.handler.WebSocketHandshakeHandler;
import com.demo.bwim.handler.WebSocketHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NettyWebSocketServer {

    @Value("${netty.websocket.port}")
    private int port;

    @Value("${netty.websocket.path}")
    private String path;

    @Value("${netty.websocket.heartbeat.reader-idle-time}")
    private int readerIdleTime;

    @Value("${netty.websocket.heartbeat.writer-idle-time}")
    private int writerIdleTime;

    @Value("${netty.websocket.heartbeat.all-idle-time}")
    private int allIdleTime;

    private final WebSocketHandler webSocketHandler;

    private final WebSocketHandshakeHandler webSocketHandshakeHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyWebSocketServer(WebSocketHandler webSocketHandler, WebSocketHandshakeHandler webSocketHandshakeHandler) {
        this.webSocketHandler = webSocketHandler;
        this.webSocketHandshakeHandler = webSocketHandshakeHandler;
    }

    @PostConstruct
    public void start() throws InterruptedException {
        log.info("[WS-SERVER] starting Netty WebSocket server, port={}, path={}, readerIdle={}s, writerIdle={}s, allIdle={}s",
                port, path, readerIdleTime, writerIdleTime, allIdleTime);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new ChunkedWriteHandler());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new IdleStateHandler(readerIdleTime, writerIdleTime, allIdleTime, TimeUnit.SECONDS));
                        pipeline.addLast(webSocketHandshakeHandler);
                        pipeline.addLast(new WebSocketServerProtocolHandler(path, true, 65536));
                        pipeline.addLast(webSocketHandler);
                        log.info("[WS-SERVER] channel pipeline initialized, channel={}", ch.id().asShortText());
                    }
                });

        ChannelFuture future = bootstrap.bind(port).sync();
        log.info("[WS-SERVER] Netty WebSocket server started successfully, port={}, path={}", port, path);
    }

    @PreDestroy
    public void stop() {
        log.info("[WS-SERVER] shutting down Netty WebSocket server");
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("[WS-SERVER] Netty WebSocket server stopped");
    }
}
