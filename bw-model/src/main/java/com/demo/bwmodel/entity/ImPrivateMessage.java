package com.demo.bwmodel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("im_private_message")
public class ImPrivateMessage implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("client_msg_id")
    private String clientMsgId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("from_user_id")
    private Long fromUserId;

    @TableField("to_user_id")
    private Long toUserId;

    private String content;

    @TableField("msg_type")
    private String msgType;

    private String extra;

    private Integer status;

    @TableField("create_time")
    private Long createTime;

    @TableField("is_recalled")
    private Integer isRecalled;

    @TableField("recall_time")
    private Long recallTime;

    @TableField("sender_deleted")
    private Integer senderDeleted;

    @TableField("receiver_deleted")
    private Integer receiverDeleted;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
