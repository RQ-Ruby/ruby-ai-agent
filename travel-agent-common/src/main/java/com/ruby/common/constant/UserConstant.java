package com.ruby.common.constant;

/**
 * 用户常量
 *
 * @author RQ
 */
public interface UserConstant {

    /**
     * 用户登录态键名（HttpSession 中保存当前登录用户）
     */
    String USER_LOGIN_STATE = "user_login";

    /**
     * 默认用户
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员
     */
    String ADMIN_ROLE = "admin";

    /**
     * 被封号
     */
    String BAN_ROLE = "ban";
}
