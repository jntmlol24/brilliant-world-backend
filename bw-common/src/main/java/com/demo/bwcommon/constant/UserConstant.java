package com.demo.bwcommon.constant;

/**
 * 用户常量
 *
 */
public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    /**
     * 被封号
     */
    String BAN_ROLE = "ban";

    /**
     * 默认头像 https://img.scdn.io/ 图床创建的 url 地址 默认60天
     */
    String DEFAULT_AVATAR = "https://img.cdn1.vip/i/69ed75670a165_1777169767.webp";

    // endregion
}
