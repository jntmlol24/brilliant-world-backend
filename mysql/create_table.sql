
-- 创建库
create database if not exists bw_db;

-- 切换库
use bw_db;


-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userEmail    varchar(512)                           null comment '邮箱',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除'
) comment '用户' collate = utf8mb4_unicode_ci;

-- 帖子表
create table if not exists post
(
    id         bigint auto_increment comment 'id' primary key,
    title      varchar(512)                       null comment '标题',
    content    text                               null comment '内容',
    tags       varchar(1024)                      null comment '标签列表（json 数组）',
    thumbNum   int      default 0                 not null comment '点赞数',
    favourNum  int      default 0                 not null comment '收藏数',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId)
) comment '帖子' collate = utf8mb4_unicode_ci;
-- 帖子点赞表（硬删除）
create table if not exists post_thumb
(
    id         bigint auto_increment comment 'id' primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_postId (postId),
    index idx_userId (userId)
) comment '帖子点赞';

-- 帖子收藏表（硬删除）
create table if not exists post_favour
(
    id         bigint auto_increment comment 'id' primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_postId (postId),
    index idx_userId (userId)
) comment '帖子收藏';

-- 帖子评论表（支持一级评论、二级回复）
create table if not exists post_comment
(
    id          bigint auto_increment comment 'id' primary key,
    postId      bigint                             not null comment '帖子id（关联post表）',
    userId      bigint                             not null comment '评论发布用户id（关联user表）',
    content     text                               not null comment '评论内容',
    parentId    bigint      default 0              not null comment '父评论id（0=一级评论，非0=回复某条评论）',
    replyUserId bigint      default 0              not null comment '被回复的用户id（仅二级回复使用）',
    likeNum     int         default 0              not null comment '评论点赞数',
    createTime  datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint     default 0              not null comment '是否删除',
    -- 索引：加速按帖子/用户/父评论查询
    index idx_postId (postId),
    index idx_userId (userId),
    index idx_parentId (parentId)
    ) comment '帖子评论表' collate = utf8mb4_unicode_ci;


-- 私聊消息表
CREATE TABLE IF NOT EXISTS im_private_message (
    id              BIGINT          NOT NULL COMMENT '消息全局唯一ID，雪花算法生成',
    client_msg_id   VARCHAR(64)     NOT NULL COMMENT '客户端生成的唯一消息ID，用于幂等',
    conversation_id VARCHAR(64)     NOT NULL COMMENT '会话ID，由双方用户ID按小_大拼接生成（如: chat_1001_1002）',
    from_user_id    BIGINT          NOT NULL COMMENT '发送者用户ID',
    to_user_id      BIGINT          NOT NULL COMMENT '接收者用户ID',
    content         TEXT            NOT NULL COMMENT '消息内容（对TEXT类型存放文本，多媒体可放JSON）',
    msg_type        VARCHAR(32)     NOT NULL DEFAULT 'TEXT' COMMENT '消息类型：TEXT IMAGE VIDEO ...',
    extra           JSON            COMMENT '扩展信息（图片宽高、缩略图、语音时长等）',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '消息状态：0-发送中 1-已送达 2-已读',
    create_time     BIGINT          NOT NULL COMMENT '消息发送时间戳（毫秒）',
    is_recalled     TINYINT         NOT NULL DEFAULT 0 COMMENT '是否撤回 0-否 1-是',
    recall_time     BIGINT          COMMENT '撤回时间戳（毫秒）',
    sender_deleted  TINYINT         NOT NULL DEFAULT 0 COMMENT '发送者是否删除 0-未删 1-已删',
    receiver_deleted TINYINT        NOT NULL DEFAULT 0 COMMENT '接收者是否删除 0-未删 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_msg_id (client_msg_id),
    KEY idx_conversation_time (conversation_id, create_time),
    KEY idx_from_user (from_user_id, create_time),
    KEY idx_to_user_status (to_user_id, status, create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私聊消息表';

-- 私聊会话表
CREATE TABLE IF NOT EXISTS im_conversation (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_id         BIGINT          NOT NULL COMMENT '当前用户ID',
    peer_user_id    BIGINT          NOT NULL COMMENT '对方用户ID',
    conversation_id VARCHAR(64)     NOT NULL COMMENT '会话ID（与消息表一致）',
    last_msg_id     BIGINT          COMMENT '最后一条消息ID',
    last_msg_content VARCHAR(512)   COMMENT '最后一条消息摘要',
    last_msg_time   BIGINT          COMMENT '最后一条消息时间',
    unread_count    INT             NOT NULL DEFAULT 0 COMMENT '当前用户未读消息数',
    last_read_msg_id BIGINT         NOT NULL DEFAULT 0 COMMENT '当前用户已读的最后一条消息ID',
    is_deleted      TINYINT         NOT NULL DEFAULT 0 COMMENT '当前用户是否删除该会话',
    create_time     BIGINT          NOT NULL COMMENT '会话创建时间',
    update_time     BIGINT          NOT NULL COMMENT '会话更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_conv (user_id, conversation_id),
    KEY idx_user_peer (user_id, peer_user_id),
    KEY idx_user_updatetime (user_id, update_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私聊会话表（每个用户对每个会话一行）';



-- 在用户列表中添加邮箱字段
-- ALTER TABLE `user`
-- ADD COLUMN userEmail varchar(512) NULL COMMENT '邮箱';
drop table if exists user;