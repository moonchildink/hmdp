package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    UserMapper mapper;

    @Resource
    StringRedisTemplate template;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号错误");
        }
        String randomCode = RandomUtil.randomNumbers(6);
        template.opsForValue().set(SystemConstants.REDIS_CODE_PREFIX + phone, randomCode, 60 * 5, TimeUnit.SECONDS);
        // 发送验证码
        log.debug("发送验证码成功，验证码：{}", randomCode);
        return Result.ok();

    }

    @Override
    public Result register(LoginFormDTO formDTO, HttpSession session) {
        String phone = formDTO.getPhone();
        String code = formDTO.getCode();
        System.out.println(formDTO);
        if (RegexUtils.isPhoneInvalid(phone) && phone.equals((String) session.getAttribute("phone"))) {
            return Result.fail("手机号错误");
        }
        String redisCode = template.opsForValue().get(SystemConstants.REDIS_CODE_PREFIX + phone);
        if (redisCode == null || !code.equals(redisCode)) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((key, value) -> value.toString()));
            UserHolder.saveUser(userDTO);
            String key = SystemConstants.REDIS_LOGIN_PREFIX + token;
            System.out.println(userMap);
            template.opsForHash().putAll(key, userMap);
            template.expire(key, SystemConstants.LOGIN_TOKEN_TIME, TimeUnit.MINUTES);
            return Result.ok(token);
        } else
            return Result.fail("手机号重复");
    }

    @Override
    public Result queryUserById(String userId) {
        // todo:查询指定id的用户
        User user = query().eq("id", userId).one();
        if (user == null)
            return Result.fail("用户不存在");
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }


    private User createUserWithPhone(String phone) {
        User user = new User().setPhone(phone).setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("phone", phone);
        return mapper.selectOne(query);
    }
}
