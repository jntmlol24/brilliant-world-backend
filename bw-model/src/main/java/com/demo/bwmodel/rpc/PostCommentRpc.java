package com.demo.bwmodel.rpc;

/**
 * 评论RPC接口（定义在公共模块 bw-model）
 */
public interface PostCommentRpc {

    /**
     * 根据帖子ID统计评论数
     */
    long countCommentByPostId(Long postId);
}