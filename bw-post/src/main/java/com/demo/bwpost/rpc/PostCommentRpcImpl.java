package com.demo.bwpost.rpc;

import com.demo.bwmodel.rpc.PostCommentRpc;
import com.demo.bwpost.service.PostCommentService;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;


/**
 * 评论RPC实现类（暴露Dubbo服务）
 */
@DubboService
public class PostCommentRpcImpl implements PostCommentRpc {

    @Resource
    private PostCommentService postCommentService;

    @Override
    public long countCommentByPostId(Long postId) {
        return postCommentService.countByPostId(postId);
    }
}