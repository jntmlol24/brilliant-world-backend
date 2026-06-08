package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImConversationQueryRequest implements Serializable {

    private Long current = 1L;

    private Long pageSize = 20L;
}
