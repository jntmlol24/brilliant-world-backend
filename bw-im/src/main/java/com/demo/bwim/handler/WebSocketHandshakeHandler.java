package com.demo.bwim.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ChannelHandler.Sharable
public class WebSocketHandshakeHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest request) {
            String channelId = ctx.channel().id().asShortText();
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
            Map<String, List<String>> parameters = queryStringDecoder.parameters();
            
            String token = getFirstParameter(parameters, "token");
            String deviceId = getFirstParameter(parameters, "deviceId");
            
            log.info("[WS-HANDSHAKE] channel={}, remote={}, token={}, deviceId={}", 
                    channelId, ctx.channel().remoteAddress(), 
                    token != null ? "present" : "absent",
                    deviceId != null ? deviceId : "absent");
            
            if (token != null) {
                ctx.channel().attr(io.netty.util.AttributeKey.valueOf("handshakeToken")).set(token);
            }
            if (deviceId != null) {
                ctx.channel().attr(io.netty.util.AttributeKey.valueOf("handshakeDeviceId")).set(deviceId);
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            String channelId = ctx.channel().id().asShortText();
            log.info("[WS-CONNECTED] channel={}, remote={}, uri={}",
                    channelId, ctx.channel().remoteAddress(),
                    ((WebSocketServerProtocolHandler.HandshakeComplete) evt).requestUri());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        log.info("[WS-DISCONNECTED] channel={}, remote={}", channelId, ctx.channel().remoteAddress());
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String channelId = ctx.channel().id().asShortText();
        log.error("[WS-HANDSHAKE-ERROR] channel={}, remote={}, error={}", 
                channelId, ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }

    private String getFirstParameter(Map<String, List<String>> parameters, String key) {
        List<String> values = parameters.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}
