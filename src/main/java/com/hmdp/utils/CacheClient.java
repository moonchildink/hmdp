package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate template;


    public CacheClient(StringRedisTemplate template) {
        this.template = template;
    }

    /**
     * 向Redis Cache中添加一个对象
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        template.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        template.opsForValue().set(key, JSONUtil.toJsonStr(value));

    }

    /**
     * @param time          : 保存对象的时长
     * @param unit：时间单位
     * @param keyPrefix：前缀
     * @param id:查询Cache的ID
     * @param type          : 数据库对象类型
     * @param dbCallBack    ： 执行数据库查询操作的回调函数
     * @param <R>
     * @param <T>
     * @return
     */
    public <R, T> R queryWithPassThrough(Long time, TimeUnit unit, String keyPrefix, T id, Class<R> type, Function<T, R> dbCallBack) {
        /**
         * 执行逻辑：
         *  1. 首先在Cache中查询执行缓存；
         *  2. 如果缓存中存在该对象，那么直接返回此对象
         *  3. 如果该对象在Cache中，并且返回字符串为空值，说明这是一个不存在的对象，为了应对缓存击穿我们直接返回空对象
         *  4. 如果该对象不在Cache中，我们需要在数据库之中进行查询，查询所得存入到缓存中，并且返回。
         */
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


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public <R, ID> R queryWithLogicalExpire(Long time, TimeUnit unit, String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack) {
        String key = keyPrefix + id;
        String shopJson = template.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        System.out.println(shopJson);
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
            // 如果没有过期直接返回对象
        }
        String lockKey = "shop:lock" + id;
        boolean triedLock = tryLock(lockKey);
        if (triedLock) {
            // 新建独立线程进行重建任务
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
//                    saveShop2Redis(id, 20L);
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    releaseLock(lockKey);
                }

            });
        }
        return r;

    }

    private boolean tryLock(String key) {
        Boolean block = template.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(block);
    }

    private boolean releaseLock(String key) {
        return Boolean.TRUE.equals(template.delete(key));
    }
}
