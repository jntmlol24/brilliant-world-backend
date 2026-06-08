package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImHistoryQueryRequest implements Serializable {

    private String conversationId;

    private Long cursorMsgId;

    /**
     * BACKWARD: older messages before cursor
     * FORWARD: newer messages after cursor
     */
    private String direction;

    private Integer pageSize;

    private Boolean reverse;
}
