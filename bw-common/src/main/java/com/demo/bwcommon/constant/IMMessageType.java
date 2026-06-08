package com.demo.bwcommon.constant;

public interface IMMessageType {

    String MESSAGE_LOGIN = "login";

    String MESSAGE_LOGOUT = "logout";

    String MESSAGE_PRIVATE_MESSAGE = "private_message";

    String MESSAGE_LOGIN_ACK = "login_ack";

    String MESSAGE_MESSAGE_ACK = "message_ack";

    String MESSAGE_MESSAGE_PUSH = "message_push";

    String MESSAGE_MESSAGE_READ = "message_read";

    String MESSAGE_READ_ACK = "read_ack";

    String MESSAGE_DELIVER_ACK = "deliver_ack";

    String MESSAGE_DELIVERED = "delivered";

    String MESSAGE_RECALL = "recall";

    String MESSAGE_DELETE = "delete";

    String MESSAGE_SYNC_OFFLINE = "offline_sync";

    String MESSAGE_CONVERSATION_SYNC = "conversation_sync";

    String MESSAGE_ONLINE_STATUS = "online_status";

    String MESSAGE_KICKOUT = "kickout";

    String MESSAGE_ERROR = "error";

    String MESSAGE_HEARTBEAT = "heartbeat";

    String MESSAGE_HEARTBEAT_ACK = "heartbeat_ack";

    String MESSAGE_DISCONNECT = "disconnect";

    String MESSAGE_DISCONNECT_ACK = "disconnect_ack";

    String MESSAGE_DISCONNECT_PUSH = "disconnect_push";

    String MESSAGE_DISCONNECT_PUSH_ACK = "disconnect_push_ack";

    String MESSAGE_ALL_READ = "all_read";
}
