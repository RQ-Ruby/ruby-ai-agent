package com.ruby.rubyaiagent.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求体
 *
 * @author RQ
 */
@Data
public class UserRegisterRequest implements Serializable {

    /** 账号 */
    private String userAccount;

    /** 密码 */
    private String userPassword;

    /** 确认密码 */
    private String checkPassword;

    @Serial
    private static final long serialVersionUID = 1L;
}
