package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImSearchUserRequest implements Serializable {

    private String keyword;

    private Integer pageSize;

    private Integer current;

    private Boolean fuzzySearch = true;

    private static final long serialVersionUID = 1L;
}
