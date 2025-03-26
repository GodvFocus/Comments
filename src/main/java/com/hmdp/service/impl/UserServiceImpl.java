package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
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
        // 3. 保存到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code);
        // 4. 设置过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_CODE_KEY + phone, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
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
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
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

        // 6. 保存到redis中
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);

        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().
                        // 空值以null形式加入新map
                        setIgnoreNullValue(true).
                        // 设置字段值编辑器，将所有字段值都转换为String类型，避免Long类型无法通过StringRedisTemplate写入redis
                        setFieldValueEditor((fieldName, fieldValue)-> fieldValue.toString()));
        // Param:is_simple=true 无下划线的简单型uuid
        String token = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, map);
        // 设置用户登录信息过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 返回token，前端将接收到token自动设置为authorization
        return Result.ok(token);
    }

    /**
     * 每日签到功能
     * @return
     */
    @Override
    public Result sign() {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期
        LocalDateTime dateTime = LocalDateTime.now();
        // 3. 拼接key
        String yearAndMonth = dateTime.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + yearAndMonth;
        // 4. 获取今天是几号
        int day = dateTime.getDayOfMonth();
        // 5. 写入redis
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    /**
     * 统计连续签到天数
     *
     * @return
     */
    @Override
    public Result signCount() {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期
        LocalDateTime dateTime = LocalDateTime.now();
        // 3. 拼接key
        String yearAndMonth = dateTime.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + yearAndMonth;
        // 4. 获取今天是几号
        int day = dateTime.getDayOfMonth();
        // 5.获取本月至今签到数据
        List<Long> bitField = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
        );
        if(bitField == null || bitField.isEmpty()){
            return Result.ok(0);
        }
        Long bitResult = bitField.get(0);
        if(bitResult == null || bitResult == 0L){
            return Result.ok(0);
        }

        int count = 0;
        while(true){
            if ((bitResult & 1) == 0) {
                break;
            }
            else {
                count++;
            }
            bitResult >>>= 1;
        }
        return Result.ok(count);
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
