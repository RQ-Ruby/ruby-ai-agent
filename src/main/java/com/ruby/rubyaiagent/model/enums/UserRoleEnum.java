package com.ruby.rubyaiagent.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

import java.util.Arrays;

/**
 * 用户角色枚举
 *
 * @author RQ
 */
@Getter
public enum UserRoleEnum {

    USER("用户", "user"),
    ADMIN("管理员", "admin"),
    BAN("被封号", "ban");

    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 反查枚举
     *
     * @param value 角色值
     * @return 枚举（找不到返回 null）
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjectUtil.isEmpty(value)) {
            return null;
        }
        return Arrays.stream(UserRoleEnum.values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
