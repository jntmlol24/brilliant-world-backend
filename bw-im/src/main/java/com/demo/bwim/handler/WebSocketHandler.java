package com.demo.bwim.handler;

import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.constant.IMMessageType;
import com.demo.bwcommon.exception.BusinessException;
import com.demo.bwim.service.ImMessageService;
import com.demo.bwim.support.ImSessionManager;
import com.demo.bwim.support.ImWsEnvelope;
import com.demo.bwim.support.ImWsSession;
import com.demo.bwmodel.dto.im.ImMessageDeleteRequest;
import com.demo.bwmodel.dto.im.ImMessageDeliverRequest;
import com.demo.bwmodel.dto.im.ImMessageReadRequest;
import com.demo.bwmodel.dto.im.ImMessageRecallRequest;
import com.demo.bwmodel.dto.im.ImSendMessageRequest;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.rpc.UserServiceRpc;
import com.demo.bwmodel.vo.ImMessageVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@ChannelHandler.Sharable
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ImSessionManager imSessionManager;

    @Resource
    private ImMessageService imMessageService;

    @DubboReference
    private UserServiceRpc userServiceRpc;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String channelId = ctx.channel().id().asShortText();
        JsonNode root = objectMapper.readTree(frame.text());
        String type = root.path("type").asText();
        JsonNode data = root.path("data");
        log.info("[WS-MSG-RECV] channel={}, type={}", channelId, type);
        try {
            switch (type) {
                case IMMessageType.MESSAGE_LOGIN -> handleLogin(ctx, data);
                case IMMessageType.MESSAGE_LOGOUT, IMMessageType.MESSAGE_DISCONNECT -> handleLogout(ctx, data);
                case IMMessageType.MESSAGE_HEARTBEAT -> handleHeartbeat(ctx);
                case IMMessageType.MESSAGE_PRIVATE_MESSAGE, "chat" -> handlePrivateMessage(ctx, data);
                case IMMessageType.MESSAGE_DELIVER_ACK -> handleDeliverAck(ctx, data);
                case IMMessageType.MESSAGE_MESSAGE_READ -> handleRead(ctx, data);
                case IMMessageType.MESSAGE_RECALL -> handleRecall(ctx, data);
                case IMMessageType.MESSAGE_DELETE -> handleDelete(ctx, data);
                default -> {
                    log.warn("[WS-MSG-RECV] channel={}, unknown type={}", channelId, type);
                    imSessionManager.send(ctx.channel(), ImWsEnvelope.error(IMMessageType.MESSAGE_ERROR, ErrorCode.PARAMS_ERROR, "unknown message type"));
                }
            }
        } catch (BusinessException e) {
            log.warn("[WS-MSG-RECV] channel={}, type={}, bizError={}: {}", channelId, type, e.getCode(), e.getMessage());
            imSessionManager.send(ctx.channel(), ImWsEnvelope.error(type, e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("[WS-MSG-RECV] channel={}, type={}, unexpected error", channelId, type, e);
            imSessionManager.send(ctx.channel(), ImWsEnvelope.error(type, ErrorCode.OPERATION_ERROR.getCode(), "internal error"));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        log.info("[WS-CHANNEL-INACTIVE] channel={}", channelId);
        ImSessionManager.UnbindResult result = imSessionManager.unbind(ctx.channel());
        if (result != null && result.isBecameOffline()) {
            log.info("[WS-CHANNEL-INACTIVE] channel={}, userId={} became offline", channelId, result.getUserId());
            imMessageService.notifyUserOnlineStatus(result.getUserId(), false, result.getLastActiveTime());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleEvent) {
            String channelId = ctx.channel().id().asShortText();
            IdleState state = idleEvent.state();
            if (state == IdleState.READER_IDLE) {
                log.warn("[WS-IDLE-TIMEOUT] channel={}, state=READER_IDLE, kicking out", channelId);
                imSessionManager.closeChannel(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_KICKOUT, "heartbeat_timeout"));
                return;
            }
            if (state == IdleState.ALL_IDLE) {
                log.warn("[WS-IDLE-TIMEOUT] channel={}, state=ALL_IDLE, kicking out", channelId);
                imSessionManager.closeChannel(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_KICKOUT, "heartbeat_timeout"));
                return;
            }
            log.debug("[WS-IDLE] channel={}, state={}, ignored", channelId, state);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String channelId = ctx.channel().id().asShortText();
        log.error("[WS-EXCEPTION] channel={}, error={}", channelId, cause.getMessage(), cause);
        ctx.close();
    }

    private void handleLogin(ChannelHandlerContext ctx, JsonNode data) {
        String channelId = ctx.channel().id().asShortText();
        String token = data.path("token").asText();
        if (StringUtils.isBlank(token)) {
            Object handshakeToken = ctx.channel().attr(io.netty.util.AttributeKey.valueOf("handshakeToken")).get();
            if (handshakeToken != null) {
                token = handshakeToken.toString();
            }
        }
        if (StringUtils.isBlank(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "token is required");
        }
        User user = userServiceRpc.getUserByToken(token);
        if (user == null) {
            log.warn("[WS-LOGIN] channel={}, invalid token", channelId);
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "invalid token");
        }
        String deviceId = data.path("deviceId").asText();
        if (StringUtils.isBlank(deviceId)) {
            Object handshakeDeviceId = ctx.channel().attr(io.netty.util.AttributeKey.valueOf("handshakeDeviceId")).get();
            if (handshakeDeviceId != null) {
                deviceId = handshakeDeviceId.toString();
            }
        }
        boolean kickOthers = data.path("kickOthers").asBoolean(false);

        log.info("[WS-LOGIN] channel={}, userId={}, deviceId={}, kickOthers={}", channelId, user.getId(), deviceId, kickOthers);
        ImSessionManager.BindResult bindResult = imSessionManager.bind(user.getId(), token, deviceId, ctx.channel(), kickOthers);

        Map<String, Object> ack = Map.of(
                "userId", user.getId(),
                "deviceId", bindResult.getSession().getDeviceId(),
                "deviceCount", bindResult.getDeviceCount(),
                "totalUnreadCount", imMessageService.getUnreadCount(user.getId(), null).getTotalUnreadCount()
        );
        imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_LOGIN_ACK, ack));
        
        List<ImMessageVO> offlineMessages = imMessageService.listOfflineMessages(user.getId(), 200);
        if (!offlineMessages.isEmpty()) {
            log.info("[WS-LOGIN] channel={}, userId={}, syncing {} offline messages", channelId, user.getId(), offlineMessages.size());
            imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_SYNC_OFFLINE, offlineMessages));
        }
        if (bindResult.isFirstOnline()) {
            log.info("[WS-LOGIN] channel={}, userId={} first online, notifying peers", channelId, user.getId());
            imMessageService.notifyUserOnlineStatus(user.getId(), true, bindResult.getSession().getLastActiveTime());
        } else {
            imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_ONLINE_STATUS,
                    Map.of("userId", user.getId(), "online", true, "lastActiveTime", bindResult.getSession().getLastActiveTime())));
        }
    }

    private void handleLogout(ChannelHandlerContext ctx, JsonNode data) {
        String channelId = ctx.channel().id().asShortText();
        Optional<ImWsSession> sessionOptional = requireSession(ctx);
        boolean invalidateToken = data.path("invalidateToken").asBoolean(false);
        sessionOptional.ifPresent(session -> {
            log.info("[WS-LOGOUT] channel={}, userId={}, invalidateToken={}", channelId, session.getUserId(), invalidateToken);
            if (invalidateToken) {
                userServiceRpc.userLogoutRpc(session.getToken());
            }
            imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_DISCONNECT_ACK, "ok"));
            ImSessionManager.UnbindResult result = imSessionManager.unbind(ctx.channel());
            if (result != null && result.isBecameOffline()) {
                imMessageService.notifyUserOnlineStatus(result.getUserId(), false, result.getLastActiveTime());
            }
            ctx.channel().close();
        });
    }

    private void handleHeartbeat(ChannelHandlerContext ctx) {
        imSessionManager.touch(ctx.channel());
        imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_HEARTBEAT_ACK, Collections.singletonMap("serverTime", System.currentTimeMillis())));
    }

    private void handlePrivateMessage(ChannelHandlerContext ctx, JsonNode data) {
        String channelId = ctx.channel().id().asShortText();
        ImWsSession session = requireSession(ctx).orElseThrow();
        ImSendMessageRequest request = convert(data, ImSendMessageRequest.class);

        log.info("[WS-PUSH-BEGIN] channel={}, senderUserId={}, toUserId={}, clientMsgId={}",
                channelId, session.getUserId(), request.getToUserId(), request.getClientMsgId());

        ImMessageVO messageVO = imMessageService.sendPrivateMessage(session.getUserId(), session.getDeviceId(), request);

        log.info("[WS-PUSH-ACK] channel={}, senderUserId={}, toUserId={}, msgId={}",
                channelId, session.getUserId(), request.getToUserId(), messageVO.getId());
        imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_MESSAGE_ACK, messageVO));
    }

    private void handleDeliverAck(ChannelHandlerContext ctx, JsonNode data) {
        ImWsSession session = requireSession(ctx).orElseThrow();
        ImMessageDeliverRequest request = convert(data, ImMessageDeliverRequest.class);
        List<Long> messageIds = request == null ? null : request.getMessageIds();
        imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_DELIVER_ACK, imMessageService.markDelivered(session.getUserId(), messageIds)));
    }

    private void handleRead(ChannelHandlerContext ctx, JsonNode data) {
        ImWsSession session = requireSession(ctx).orElseThrow();
        ImMessageReadRequest request = convert(data, ImMessageReadRequest.class);
        imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_READ_ACK, imMessageService.markRead(session.getUserId(), request)));
    }

    private void handleRecall(ChannelHandlerContext ctx, JsonNode data) {
        ImWsSession session = requireSession(ctx).orElseThrow();
        ImMessageRecallRequest request = convert(data, ImMessageRecallRequest.class);
        imMessageService.recallMessage(session.getUserId(), request);
        imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_RECALL, "ok"));
    }

    private void handleDelete(ChannelHandlerContext ctx, JsonNode data) {
        ImWsSession session = requireSession(ctx).orElseThrow();
        ImMessageDeleteRequest request = convert(data, ImMessageDeleteRequest.class);
        imMessageService.deleteMessage(session.getUserId(), request);
        imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_DELETE, "ok"));
    }

    private Optional<ImWsSession> requireSession(ChannelHandlerContext ctx) {
        Optional<ImWsSession> sessionOptional = imSessionManager.getSession(ctx.channel());
        if (sessionOptional.isEmpty()) {
            log.warn("[WS-AUTH-FAIL] channel={}, not authenticated", ctx.channel().id().asShortText());
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "connection not authenticated");
        }
        return sessionOptional;
    }

    private <T> T convert(JsonNode data, Class<T> clazz) {
        return objectMapper.convertValue(data, clazz);
    }
}
