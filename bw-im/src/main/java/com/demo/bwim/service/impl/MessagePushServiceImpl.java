package com.demo.bwim.service.impl;

import com.demo.bwim.service.MessagePushService;
import com.demo.bwim.support.ImSessionManager;
import com.demo.bwim.support.ImWsEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collection;

@Slf4j
@Service
public class MessagePushServiceImpl implements MessagePushService {

    @Resource
    private ImSessionManager imSessionManager;

    @Override
    public void pushToUser(Long userId, ImWsEnvelope envelope) {
        log.info("[MSG-PUSH] pushing to user, targetUserId={}, envelopeType={}, timestamp={}",
                userId, envelope.getType(), envelope.getTimestamp());
        int sentCount = imSessionManager.sendToUser(userId, envelope);
        log.info("[MSG-PUSH] push result, targetUserId={}, sentCount={}", userId, sentCount);
    }

    @Override
    public void pushToUsers(Collection<Long> userIds, ImWsEnvelope envelope) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        log.info("[MSG-PUSH-BATCH] pushing to {} users, envelopeType={}, timestamp={}",
                userIds.size(), envelope.getType(), envelope.getTimestamp());
        imSessionManager.sendToUsers(userIds, envelope);
    }
}