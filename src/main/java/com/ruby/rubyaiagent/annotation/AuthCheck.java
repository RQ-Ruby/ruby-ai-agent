package com.ruby.rubyaiagent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解：标注在 controller 方法上，指定该接口要求的最低角色。
 *
 * @author RQ
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须具有的角色（user / admin）。
     * 默认为空字符串：仅校验登录态。
     */
    String mustRole() default "";
}
