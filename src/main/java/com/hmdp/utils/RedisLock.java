package com.hmdp.utils;


import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisLock implements iLock {
    private StringRedisTemplate template;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";


    public RedisLock(String name, StringRedisTemplate template) {
        this.name = name;
        this.template = template;
    }

    /**
     * 使用随机生成的UUID+线程ID作为锁的内容
     *
     * @param ttl
     * @return
     */
    @Override
    public boolean tryLock(long ttl) {

        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = template.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", ttl, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void releaseLock() {
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        String s = template.opsForValue().get(KEY_PREFIX + name);
        if (s != null && s.equals(threadID))
            template.delete(KEY_PREFIX + name);
    }
}
