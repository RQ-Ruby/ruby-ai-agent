package com.ruby.rubyaiagent.aop;

import com.ruby.rubyaiagent.annotation.AuthCheck;
import com.ruby.rubyaiagent.exception.BusinessException;
import com.ruby.rubyaiagent.exception.ErrorCode;
import com.ruby.rubyaiagent.model.entity.User;
import com.ruby.rubyaiagent.model.enums.UserRoleEnum;
import com.ruby.rubyaiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限校验 AOP：拦截被 {@link AuthCheck} 标注的方法，
 * 校验登录态 + 角色，未通过抛 BusinessException 由 GlobalExceptionHandler 统一返回。
 *
 * @author RQ
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        // 从当前 request context 拿到 HttpServletRequest
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();

        // 1. 必须登录
        User loginUser = userService.getLoginUser(request);

        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 2. 若没指定 mustRole，仅校验登录
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // 3. 校验当前用户角色
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 如果需要 admin，但当前不是 admin → 拒绝
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过
        return joinPoint.proceed();
    }
}
