package com.ruby.rubyaiagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruby.rubyaiagent.annotation.AuthCheck;
import com.ruby.rubyaiagent.common.BaseResponse;
import com.ruby.rubyaiagent.common.DeleteRequest;
import com.ruby.rubyaiagent.common.ResultUtils;
import com.ruby.rubyaiagent.constant.UserConstant;
import com.ruby.rubyaiagent.exception.ErrorCode;
import com.ruby.rubyaiagent.exception.ThrowUtils;
import com.ruby.rubyaiagent.model.dto.user.UserAddRequest;
import com.ruby.rubyaiagent.model.dto.user.UserLoginRequest;
import com.ruby.rubyaiagent.model.dto.user.UserQueryRequest;
import com.ruby.rubyaiagent.model.dto.user.UserRegisterRequest;
import com.ruby.rubyaiagent.model.dto.user.UserUpdateRequest;
import com.ruby.rubyaiagent.model.entity.User;
import com.ruby.rubyaiagent.model.vo.LoginUserVO;
import com.ruby.rubyaiagent.model.vo.UserVO;
import com.ruby.rubyaiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 用户接口
 *
 * @author RQ
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    // region 登录注册

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest req) {
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR);
        long id = userService.userRegister(req.getUserAccount(), req.getUserPassword(), req.getCheckPassword());
        return ResultUtils.success(id);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest req, HttpServletRequest request) {
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR);
        LoginUserVO vo = userService.userLogin(req.getUserAccount(), req.getUserPassword(), request);
        return ResultUtils.success(vo);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(user));
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        return ResultUtils.success(userService.userLogout(request));
    }

    // endregion

    // region 用户管理（仅 admin）

    /**
     * 新增用户（默认密码 12345678）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest req) {
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(req, user);
        String defaultPwd = "12345678";
        user.setUserPassword(userService.getEncryptPassword(defaultPwd));
        boolean ok = userService.save(user);
        ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest req) {
        ThrowUtils.throwIf(req == null || req.getId() == null || req.getId() <= 0, ErrorCode.PARAMS_ERROR);
        boolean ok = userService.removeById(req.getId());
        return ResultUtils.success(ok);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest req) {
        ThrowUtils.throwIf(req == null || req.getId() == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(req, user);
        boolean ok = userService.updateById(user);
        ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 查询用户（admin 可见完整 User）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 查询用户脱敏视图（任意登录用户可见）
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(Long id) {
        BaseResponse<User> resp = getUserById(id);
        return ResultUtils.success(userService.getUserVO(resp.getData()));
    }

    /**
     * 分页获取用户脱敏列表
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest req) {
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR);
        long current = req.getCurrent();
        long size = req.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, size), userService.getQueryWrapper(req));
        Page<UserVO> voPage = new Page<>(current, size, userPage.getTotal());
        voPage.setRecords(userService.getUserVOList(userPage.getRecords()));
        return ResultUtils.success(voPage);
    }

    // endregion
}
