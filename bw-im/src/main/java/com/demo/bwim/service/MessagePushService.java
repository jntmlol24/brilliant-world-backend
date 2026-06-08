package com.demo.bwim.service;

import com.demo.bwim.support.ImWsEnvelope;

import java.util.Collection;

public interface MessagePushService {

    void pushToUser(Long userId, ImWsEnvelope envelope);

    void pushToUsers(Collection<Long> userIds, ImWsEnvelope envelope);
}