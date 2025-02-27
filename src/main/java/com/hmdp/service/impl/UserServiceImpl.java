package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 检验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail(SystemConstants.PHONE_ERROR);
        }
        // 2. 符合则生成校验码
        String code = RandomUtil.randomNumbers(6);
        // 3. 保存到session
        session.setAttribute("code", code);
        log.debug("验证码:" + code);
        return Result.ok();
    }

    /**
     * 登录、注册功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
         String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail(SystemConstants.PHONE_ERROR);
        }
        // 2. 校验验证码
        String code = session.getAttribute("code").toString();
        // 3. 验证码正确则根据手机号查询用户
        if(code == null || !code.equals(loginForm.getCode())){
            return Result.fail(SystemConstants.CODE_ERROR);
        }
        // 单表查询
        User user = query().eq("phone", phone).one();
        // 4. 判断用户是否存在
        if(user == null){
            // 5. 不存在则自动注册
            user = createNewUser(loginForm);
        }

        // 6. 保存到session中
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        session.setAttribute("user", userDTO);

        return Result.ok();
    }

    /**
     * 注册新用户
     * @param loginForm
     * @return
     */
    private User createNewUser(LoginFormDTO loginForm) {
        User user = new User();
        user.setPhone(loginForm.getPhone());
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        save(user);
        return user;

    }
}
