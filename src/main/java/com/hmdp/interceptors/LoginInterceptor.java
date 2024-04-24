package com.hmdp.interceptors;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate template) {
        this.stringRedisTemplate = template;
    }

    /**
     * 此处的用户缓存TTL是指：用户在一段时间内没访问服务器以后，将相关信息从服务器删除
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(SystemConstants.REDIS_LOGIN_PREFIX + token);

        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }
        UserDTO userDto = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDto);

        stringRedisTemplate.expire(SystemConstants.REDIS_LOGIN_PREFIX + token, SystemConstants.LOGIN_TOKEN_TIME, TimeUnit.MINUTES);
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
