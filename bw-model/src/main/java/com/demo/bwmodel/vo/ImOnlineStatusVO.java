package com.demo.bwmodel.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImOnlineStatusVO implements Serializable {

    private Long userId;

    private Boolean online;

    private Long lastActiveTime;
}
