package com.demo.bwpost.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.bwcommon.annotation.AuthCheck;
import com.demo.bwcommon.common.BaseResponse;
import com.demo.bwcommon.common.ResultUtils;
import com.demo.bwmodel.dto.postcomment.AddCommentDTO;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.rpc.UserServiceRpc;
import com.demo.bwmodel.vo.CommentVO;
import com.demo.bwpost.service.PostCommentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * 评论接口（路由：/api/post/comment）
 */
@RestController
@RequestMapping("comment")
@Slf4j
public class PostCommentController {

    @Resource
    private PostCommentService postCommentService;

    @DubboReference
    private UserServiceRpc userService;

    /**
     * 添加评论（登录可用）
     */
    @PostMapping("/add")
    @AuthCheck
    public BaseResponse<Boolean> addComment(@RequestBody @Valid AddCommentDTO dto) {
        Long userId = userService.getUserByRedis().getId();
        return ResultUtils.success(postCommentService.addComment(dto, userId));
    }

    /**
     * 删除评论（登录可用）
     */
    @PostMapping("/delete/{id}")
    @AuthCheck
    public BaseResponse<Boolean> deleteComment(@PathVariable Long id) {
        User user = userService.getUserByRedis();
        Long userId = user.getId();
        String userRole = user.getUserRole();
        return ResultUtils.success(postCommentService.deleteComment(id, userId, userRole));
    }

    /**
     * 分页查询帖子评论
     */
    @GetMapping("/list/{postId}")
    public BaseResponse<Page<CommentVO>> listComment(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ResultUtils.success(postCommentService.listCommentByPage(postId, current, size));
    }

    /**
     * 获取帖子评论总数
     */
    @GetMapping("/count/{postId}")
    public BaseResponse<Long> countComment(@PathVariable Long postId) {
        return ResultUtils.success(postCommentService.countByPostId(postId));
    }
}