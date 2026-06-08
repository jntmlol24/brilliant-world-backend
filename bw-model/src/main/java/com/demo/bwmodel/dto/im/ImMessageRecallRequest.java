package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImMessageRecallRequest implements Serializable {

    private Long messageId;
}
