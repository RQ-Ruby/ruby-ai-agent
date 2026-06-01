package com.ruby.user.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruby.common.constant.UserConstant;
import com.ruby.common.exception.BusinessException;
import com.ruby.common.exception.ErrorCode;
import com.ruby.common.exception.ThrowUtils;
import com.ruby.model.dto.user.UserQueryRequest;
import com.ruby.model.entity.User;
import com.ruby.model.enums.UserRoleEnum;
import com.ruby.model.vo.LoginUserVO;
import com.ruby.model.vo.UserVO;
import com.ruby.user.mapper.UserMapper;
import com.ruby.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 *
 * @author RQ
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值（混淆密码，参考鱼皮项目命名习惯）
     */
    private static final String SALT = "ruby";

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword),
                ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "账号过短");
        ThrowUtils.throwIf(userPassword.length() < 8 || checkPassword.length() < 8,
                ErrorCode.PARAMS_ERROR, "密码过短");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword),
                ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");

        // 2. 账号唯一性
        long count = this.lambdaQuery().eq(User::getUserAccount, userAccount).count();
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "账号重复");

        // 3. 密码加密
        String encryptPassword = getEncryptPassword(userPassword);

        // 4. 插入
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saved = this.save(user);
        ThrowUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword),
                ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "账号错误");
        ThrowUtils.throwIf(userPassword.length() < 8, ErrorCode.PARAMS_ERROR, "密码错误");

        // 2. 加密后查询
        String encryptPassword = getEncryptPassword(userPassword);
        User user = this.lambdaQuery()
                .eq(User::getUserAccount, userAccount)
                .eq(User::getUserPassword, encryptPassword)
                .one();
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword: {}", userAccount);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        ThrowUtils.throwIf(UserRoleEnum.BAN.getValue().equals(user.getUserRole()),
                ErrorCode.FORBIDDEN_ERROR, "该账号已被封禁");

        // 3. 写入 session
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        return getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (userObj instanceof User u) ? u : null;
        ThrowUtils.throwIf(currentUser == null || currentUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);
        // 从数据库再查一次，确保用户最新（角色变化、是否被封禁等）
        User user = this.getById(currentUser.getId());
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
        return user;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        ThrowUtils.throwIf(userObj == null, ErrorCode.OPERATION_ERROR, "未登录");
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO vo = new LoginUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return List.of();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // MD5(SALT + password)
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest req) {
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR, "请求参数为空");
        Long id = req.getId();
        String userAccount = req.getUserAccount();
        String userName = req.getUserName();
        String userProfile = req.getUserProfile();
        String userRole = req.getUserRole();
        String sortField = req.getSortField();
        String sortOrder = req.getSortOrder();

        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq(id != null, "id", id);
        qw.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        qw.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        qw.like(StrUtil.isNotBlank(userName), "userName", userName);
        qw.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        // 安全的排序字段白名单
        String safeSortField = Optional.ofNullable(sortField)
                .filter(f -> List.of("createTime", "updateTime", "id").contains(f))
                .orElse("createTime");
        boolean asc = "ascend".equalsIgnoreCase(sortOrder);
        qw.orderBy(true, asc, safeSortField);
        return qw;
    }
}
