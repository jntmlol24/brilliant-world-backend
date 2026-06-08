package com.demo.bwpost.controller;


import com.demo.bwcommon.common.BaseResponse;
import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.common.ResultUtils;
import com.demo.bwcommon.exception.BusinessException;
import com.demo.bwmodel.dto.post.PostAddRequest;
import com.demo.bwmodel.dto.postfavour.PostFavourAddRequest;
import com.demo.bwmodel.dto.postthumb.PostThumbAddRequest;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.rpc.UserServiceRpc;
import com.demo.bwpost.service.PostThumbService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.demo.bwcommon.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 帖子点赞接口
 *
 */
@RestController
@RequestMapping("/post_thumb")
@Slf4j
public class PostThumbController {

    @Resource
    private PostThumbService postThumbService;

    @DubboReference
    private UserServiceRpc userService;

    /**
     * 点赞 / 取消点赞
     *
     * @param postThumbAddRequest
     * @param request
     * @return resultNum 本次点赞变化数
     */
    @PostMapping("/")
    public BaseResponse<Integer> doThumb(@RequestBody PostThumbAddRequest postThumbAddRequest,
                                         HttpServletRequest request) {
        if (postThumbAddRequest == null || postThumbAddRequest.getPostId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录才能点赞
        final User loginUser = userService.getUserByRedis();
        long postId = postThumbAddRequest.getPostId();
        int result = postThumbService.doPostThumb(postId, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/judge")
    public BaseResponse<Boolean> judgeFavourOrNot(@RequestBody PostThumbAddRequest postThumbAddRequest,
                                                  HttpServletRequest request) {
        if (postThumbAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getUserByRedis();
        return ResultUtils.success(postThumbService.judgeThumbOrNot(postThumbAddRequest, loginUser.getId()));
    }

}
