package com.ruby.user.aop;

import com.ruby.common.annotation.AuthCheck;
import com.ruby.common.exception.BusinessException;
import com.ruby.common.exception.ErrorCode;
import com.ruby.model.entity.User;
import com.ruby.model.enums.UserRoleEnum;
import com.ruby.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限校验 AOP：拦截被 AuthCheck 标注的方法，
 * 校验登录态 + 角色，未通过抛 BusinessException 由 GlobalExceptionHandler 统一返回。
 *
 * @author RQ
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * @description 权限校验 AOP：拦截被 AuthCheck 标注的方法，校验登录态 + 角色
     * @return: java.lang.Object
     * @author RQ
     * @date: 2026/6/5 下午8:00
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        // 1.从当前请求中解析出 HttpServletRequest，里面封装了 session 等信息
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        // 2.从 HttpServletRequest 的 session 中获取当前登录用户
        HttpServletRequest request = attrs.getRequest();
        User loginUser = userService.getLoginUser(request);

        // 3. 执行鉴权
        // 取出注解中要求的角色枚举
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 若没指定 mustRole，仅校验登录
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 校验当前用户角色
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 如果需要 admin，但当前不是 admin 则 拒绝
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过
        return joinPoint.proceed();
    }
}
