package com.ruby.rubyaiagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ruby.rubyaiagent.model.dto.user.UserQueryRequest;
import com.ruby.rubyaiagent.model.entity.User;
import com.ruby.rubyaiagent.model.vo.LoginUserVO;
import com.ruby.rubyaiagent.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户服务
 *
 * @author RQ
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      用于写入 session
     * @return 脱敏后的登录用户
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request request
     * @return 当前登录用户（未登录抛 BusinessException）
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request request
     * @return 是否成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 是否为管理员
     */
    boolean isAdmin(User user);

    /**
     * 获取脱敏后的登录用户
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏后的用户视图
     */
    UserVO getUserVO(User user);

    /**
     * 批量脱敏
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 加密密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 构造分页查询条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
}
