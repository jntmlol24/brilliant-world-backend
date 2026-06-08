package com.demo.bwim.support;

import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.exception.BusinessException;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.rpc.UserServiceRpc;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Component
public class ImAuthHelper {

    @DubboReference
    private UserServiceRpc userServiceRpc;

    public User requireUser(HttpServletRequest request) {
        String token = resolveToken(request);
        return userServiceRpc.getUserByToken(token);
    }

    public Long requireUserId(HttpServletRequest request) {
        return requireUser(request).getId();
    }

    public String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        String token = request.getHeader("token");
        if (StringUtils.isNotBlank(token)) {
            return token;
        }
        token = request.getParameter("token");
        if (StringUtils.isBlank(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "token is required");
        }
        return token;
    }
}
