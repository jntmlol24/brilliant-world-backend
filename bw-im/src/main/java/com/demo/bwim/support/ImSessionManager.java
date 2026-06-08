package com.demo.bwim.support;

import com.demo.bwcommon.constant.IMMessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ImSessionManager {

    private static final String ONLINE_KEY_PREFIX = "im:online:user:";

    private final ConcurrentMap<Long, ConcurrentMap<String, Channel>> userChannels = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, ImWsSession> channelSessions = new ConcurrentHashMap<>();

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    public BindResult bind(Long userId, String token, String deviceId, Channel channel, boolean kickOthers) {
        String actualDeviceId = StringUtils.defaultIfBlank(deviceId, channel.id().asShortText());
        String channelId = channel.id().asShortText();
        unbind(channel);

        ConcurrentMap<String, Channel> deviceMap = userChannels.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
        Channel oldSameDevice = deviceMap.put(actualDeviceId, channel);
        if (oldSameDevice != null && oldSameDevice != channel) {
            log.info("[SESSION-BIND] userId={}, deviceId={}, channel={}, replacing old channel of same device",
                    userId, actualDeviceId, channelId);
            closeChannel(oldSameDevice, ImWsEnvelope.ok(IMMessageType.MESSAGE_KICKOUT, "same_device_replaced"));
        }

        if (kickOthers) {
            List<Map.Entry<String, Channel>> channelsToKick = deviceMap.entrySet().stream()
                    .filter(entry -> !Objects.equals(entry.getKey(), actualDeviceId))
                    .toList();
            for (Map.Entry<String, Channel> entry : channelsToKick) {
                log.info("[SESSION-BIND] userId={}, kicking deviceId={}", userId, entry.getKey());
                closeChannel(entry.getValue(), ImWsEnvelope.ok(IMMessageType.MESSAGE_KICKOUT, "kicked_by_new_login"));
            }
        }

        long now = System.currentTimeMillis();
        ImWsSession session = ImWsSession.builder()
                .userId(userId)
                .token(token)
                .deviceId(actualDeviceId)
                .channelId(channel.id().asLongText())
                .loginTime(now)
                .lastActiveTime(now)
                .build();
        channelSessions.put(channel.id().asLongText(), session);

        boolean wasOnline = hashSize(userId) > 0;
        touch(userId, actualDeviceId, now);
        BindResult result = new BindResult(session, !wasOnline, deviceMap.size());
        log.info("[SESSION-BIND] userId={}, deviceId={}, channel={}, firstOnline={}, deviceCount={}",
                userId, actualDeviceId, channelId, result.isFirstOnline(), result.getDeviceCount());
        return result;
    }

    public UnbindResult unbind(Channel channel) {
        if (channel == null) {
            return null;
        }
        String channelId = channel.id().asShortText();
        ImWsSession session = channelSessions.remove(channel.id().asLongText());
        if (session == null) {
            return null;
        }
        ConcurrentMap<String, Channel> deviceMap = userChannels.get(session.getUserId());
        if (deviceMap != null) {
            deviceMap.remove(session.getDeviceId(), channel);
            if (deviceMap.isEmpty()) {
                userChannels.remove(session.getUserId(), deviceMap);
            }
        }
        removePresence(session.getUserId(), session.getDeviceId());
        boolean online = isUserOnline(session.getUserId());
        UnbindResult result = new UnbindResult(session.getUserId(), session.getDeviceId(), session.getLastActiveTime(), !online);
        log.info("[SESSION-UNBIND] userId={}, deviceId={}, channel={}, becameOffline={}",
                result.getUserId(), result.getDeviceId(), channelId, result.isBecameOffline());
        return result;
    }

    public Optional<ImWsSession> getSession(Channel channel) {
        if (channel == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(channelSessions.get(channel.id().asLongText()));
    }

    public void touch(Channel channel) {
        getSession(channel).ifPresent(session -> touch(session.getUserId(), session.getDeviceId(), System.currentTimeMillis()));
    }

    public int sendToUser(Long userId, Object payload) {
        return sendToUser(userId, payload, null);
    }

    public int sendToUser(Long userId, Object payload, String excludeDeviceId) {
        ConcurrentMap<String, Channel> deviceMap = userChannels.get(userId);
        if (deviceMap == null || deviceMap.isEmpty()) {
            log.info("[WS-PUSH-USER] userId={}, no active channels, user offline", userId);
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, Channel> entry : deviceMap.entrySet()) {
            if (Objects.equals(entry.getKey(), excludeDeviceId)) {
                continue;
            }
            String chId = entry.getValue().id().asShortText();
            log.info("[WS-PUSH-SENDING] userId={}, deviceId={}, channel={}", userId, entry.getKey(), chId);
            boolean sent = send(entry.getValue(), payload);
            if (sent) {
                count++;
                log.info("[WS-PUSH-SENT] userId={}, deviceId={}, channel={}", userId, entry.getKey(), chId);
            } else {
                log.warn("[WS-PUSH-FAIL] userId={}, deviceId={}, channel={}, send failed", userId, entry.getKey(), chId);
            }
        }
        return count;
    }

    public void sendToUsers(Collection<Long> userIds, Object payload) {
        if (userIds == null) {
            return;
        }
        userIds.stream().filter(Objects::nonNull).distinct().forEach(userId -> sendToUser(userId, payload));
    }

    public boolean send(Channel channel, Object payload) {
        if (channel == null || !channel.isActive()) {
            log.warn("[WS-SEND] channel inactive or null");
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            channel.writeAndFlush(new TextWebSocketFrame(json));
            return true;
        } catch (JsonProcessingException e) {
            log.error("[WS-SEND] channel={}, serialize payload failed", channel.id().asShortText(), e);
            return false;
        }
    }

    public void closeChannel(Channel channel, Object payload) {
        if (channel == null) {
            return;
        }
        String channelId = channel.id().asShortText();
        log.info("[WS-CLOSE] channel={}, hasPayload={}", channelId, payload != null);
        if (payload != null) {
            send(channel, payload);
        }
        channel.close();
    }

    public boolean isUserOnline(Long userId) {
        return hashSize(userId) > 0;
    }

    public Long getLastActiveTime(Long userId) {
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        List<Object> values = new ArrayList<>(hashOperations.values(buildOnlineKey(userId)));
        return values.stream()
                .map(String::valueOf)
                .filter(StringUtils::isNotBlank)
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .max(Long::compareTo)
                .orElse(0L);
    }

    public Map<Long, Boolean> batchOnlineStatus(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> distinctIds = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Boolean> result = new java.util.HashMap<>(distinctIds.size());
        for (Long userId : distinctIds) {
            result.put(userId, isUserOnline(userId));
        }
        return result;
    }

    private void touch(Long userId, String deviceId, long timestamp) {
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        hashOperations.put(buildOnlineKey(userId), deviceId, String.valueOf(timestamp));
        ConcurrentMap<String, Channel> channels = userChannels.get(userId);
        if (channels != null) {
            channels.entrySet().stream()
                    .filter(entry -> Objects.equals(entry.getKey(), deviceId))
                    .findFirst()
                    .flatMap(entry -> getSession(entry.getValue()))
                    .ifPresent(session -> session.setLastActiveTime(timestamp));
        }
    }

    private void removePresence(Long userId, String deviceId) {
        redisTemplate.opsForHash().delete(buildOnlineKey(userId), deviceId);
    }

    private long hashSize(Long userId) {
        Long size = redisTemplate.opsForHash().size(buildOnlineKey(userId));
        return size == null ? 0L : size;
    }

    private String buildOnlineKey(Long userId) {
        return ONLINE_KEY_PREFIX + userId;
    }

    @Data
    @AllArgsConstructor
    public static class BindResult {
        private ImWsSession session;
        private boolean firstOnline;
        private int deviceCount;
    }

    @Data
    @AllArgsConstructor
    public static class UnbindResult {
        private Long userId;
        private String deviceId;
        private Long lastActiveTime;
        private boolean becameOffline;
    }
}
