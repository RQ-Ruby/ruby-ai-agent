package com.ruby.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体
 *
 * @author RQ
 */
@TableName(value = "user")
@Data
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * id（雪花算法生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 账号
     */
    private String userAccount;
    /**
     * 密码（MD5(SALT + password)）
     */
    private String userPassword;
    /**
     * 用户昵称
     */
    private String userName;
    /**
     * 用户头像
     */
    private String userAvatar;
    /**
     * 用户简介
     */
    private String userProfile;
    /**
     * 用户角色：user / admin / ban
     */
    private String userRole;
    /**
     * 编辑时间
     */
    private Date editTime;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 是否删除（逻辑删除）
     */
    @TableLogic
    @TableField(value = "isDelete")
    private Integer isDelete;
}
