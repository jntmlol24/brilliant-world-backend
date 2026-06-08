package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImMessageQueryRequest implements Serializable {

    private String conversationId;

    private Long cursorMsgId;

    private String direction;

    private Integer pageSize;

    private Boolean reverse;
}
