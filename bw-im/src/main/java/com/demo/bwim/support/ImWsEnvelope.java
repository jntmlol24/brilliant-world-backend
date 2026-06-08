package com.demo.bwim.support;

import com.demo.bwcommon.common.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImWsEnvelope implements Serializable {

    private String type;

    private Integer code;

    private String message;

    private Long timestamp;

    private Object data;

    public static ImWsEnvelope ok(String type, Object data) {
        return ImWsEnvelope.builder()
                .type(type)
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .timestamp(System.currentTimeMillis())
                .data(data)
                .build();
    }

    public static ImWsEnvelope error(String type, ErrorCode errorCode, String message) {
        return ImWsEnvelope.builder()
                .type(type)
                .code(errorCode.getCode())
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ImWsEnvelope error(String type, int code, String message) {
        return ImWsEnvelope.builder()
                .type(type)
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
