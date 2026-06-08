package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImMessageDeleteRequest implements Serializable {

    private Long messageId;
}
