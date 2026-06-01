package com.ruby.model.dto.user;

import com.ruby.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户分页查询请求体（管理员使用）
 *
 * @author RQ
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * id
     */
    private Long id;
    /**
     * 账号
     */
    private String userAccount;
    /**
     * 用户昵称
     */
    private String userName;
    /**
     * 用户简介
     */
    private String userProfile;
    /**
     * 用户角色：user / admin / ban
     */
    private String userRole;
}
