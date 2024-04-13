package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate template;


    public CacheClient(StringRedisTemplate template) {
        this.template = template;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        template.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        template.opsForValue().set(key, JSONUtil.toJsonStr(value));

    }


    public <R, T> R queryWithPassThrough(Long time, TimeUnit unit, String keyPrefix, T id, Class<R> type, Function<T, R> dbCallBack) {
        String key = keyPrefix + id;
        String json = template.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        R r = dbCallBack.apply(id);
        if (r == null) {
            template.opsForValue().set(key, "", SystemConstants.REDIS_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(key, r, time, unit);
        return r;
    }
}
