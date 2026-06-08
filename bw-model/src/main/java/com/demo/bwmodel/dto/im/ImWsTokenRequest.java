package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImWsTokenRequest implements Serializable {

    private String deviceId;

    private Boolean kickOthers;
}
