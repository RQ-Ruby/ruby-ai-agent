package com.ruby.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解,被 AOP 拦截后校验登录态
 * 方法注解，指定该接口要求调用方的最低角色
 *
 * @author RQ
 * @Target ElementType.METHOD 指定为方法注解
 * @Retention RetentionPolicy.RUNTIME 指定为运行时注解
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
