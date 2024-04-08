package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.util.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Random;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    UserMapper mapper;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号错误");
        }

        String randomCode = RandomUtil.randomNumbers(6);
        session.setAttribute(SystemConstants.SESSION_CODE_ATTRIBUTE, randomCode);
        session.setAttribute(SystemConstants.SESSION_PHONE_ATTRIBUTE, phone);
        // 发送验证码
        log.debug("发送验证码成功，验证码：{}", randomCode);
        return Result.ok();

    }

    @Override
    public Result register(LoginFormDTO formDTO, HttpSession session) {
        String phone = formDTO.getPhone();
        String code = formDTO.getCode();
        if (RegexUtils.isPhoneInvalid(phone) && phone.equals((String) session.getAttribute("phone"))) {
            return Result.fail("手机号错误");
        }
        Object sessionCode = session.getAttribute("code");
        if (sessionCode == null || !code.equals(sessionCode)) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
            if(save(user)){
                UserDTO userDTO = new UserDTO().setId(user.getId()).setNickName(user.getNickName()).setIcon(user.getIcon());
                session.setAttribute("user", userDTO);
                return Result.ok();
            }else
                return Result.fail("未知错误");

        } else
            return Result.fail("手机号重复");

    }

    private User createUserWithPhone(String phone) {
        return new User().setPhone(phone).setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
    }
}
