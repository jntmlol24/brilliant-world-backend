package com.demo.bwmodel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("im_conversation")
public class ImConversation implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("peer_user_id")
    private Long peerUserId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("last_msg_id")
    private Long lastMsgId;

    @TableField("last_msg_content")
    private String lastMsgContent;

    @TableField("last_msg_time")
    private Long lastMsgTime;

    @TableField("unread_count")
    private Integer unreadCount;

    @TableField("last_read_msg_id")
    private Long lastReadMsgId;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("create_time")
    private Long createTime;

    @TableField("update_time")
    private Long updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
