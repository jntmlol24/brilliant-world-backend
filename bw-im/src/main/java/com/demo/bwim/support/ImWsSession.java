package com.demo.bwim.support;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImWsSession {

    private Long userId;

    private String token;

    private String deviceId;

    private String channelId;

    private Long loginTime;

    private Long lastActiveTime;
}
