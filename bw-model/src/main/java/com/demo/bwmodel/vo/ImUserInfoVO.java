package com.demo.bwmodel.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImUserInfoVO implements Serializable {

    private Long userId;

    private String userAccount;

    private String userAvatar;

    private Boolean online;
}
