package com.ruby.client.innerService;

import com.ruby.common.constant.UserConstant;
import com.ruby.common.exception.BusinessException;
import com.ruby.common.exception.ErrorCode;
import com.ruby.model.entity.User;
import com.ruby.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public interface InnerUserService {

    /**
     * 由于 HttpServletRequest 对象不好在网络中传递，因此采用静态方法，避免跨服务调用
     */
    static User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    List<User> listByIds(Collection<? extends Serializable> ids);

    User getById(Serializable id);

    UserVO getUserVO(User user);
}