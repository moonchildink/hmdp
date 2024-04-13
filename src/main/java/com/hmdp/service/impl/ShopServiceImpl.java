package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Sinks;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private CacheClient cacheClient;


    @Resource
    private StringRedisTemplate template;

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 1. 先更新数据库
        updateById(shop);
        // 2. 在删除缓存
        if (shop.getId() != null) {
            template.delete(SystemConstants.REDIS_CACHE + shop.getId());
            return Result.ok();
        }
        return Result.fail("Shop ID can't be null");

    }

    @Override
    public Result queryShopById(Long id) {
//        String shopJson = template.opsForValue().get(SystemConstants.REDIS_CACHE + id);
//        if (shopJson == null) {
//            // 说明不存在于Cache之中
//            Shop shop = getById(id);
//            if (shop == null) {
//                // 说明也不存在于数据库之中,那么在Cache中设置一个空字符串
//                template.opsForValue().set(SystemConstants.REDIS_CACHE + String.valueOf(id), "", 3L, TimeUnit.MINUTES);
//                return Result.fail("商铺不存在");
//            }
//            template.opsForValue().set(SystemConstants.REDIS_CACHE + id, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
//            return Result.ok(shop);
//        }
//        // 在缓存之中
//        // 1. 空对象
//        if (shopJson.equals(""))
//            return Result.fail("商铺不存在");
//        return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        String shopJson = template.opsForValue().get(SystemConstants.REDIS_CACHE + id);
        Shop shop = null;
        if (shopJson == null) {
            // 说明不存在于Cache之中,进行缓存重建
            String lockKey = "lock:shop:" + id;
            try {
                boolean isSuccess = tryLock(lockKey);
                if (!isSuccess) {
                    Thread.sleep(50);
                    // 重试查询
                    return queryWithMutex(id);
                }
                // 获取成功,查询数据库，存入Redis，释放lock
                shop = getById(id);

                // 模拟数据库重建延迟
                Thread.sleep(200);
                if (shop == null) {
                    // 说明也不存在于数据库之中,那么在Cache中设置一个空字符串
                    template.opsForValue().set(SystemConstants.REDIS_CACHE + String.valueOf(id), "", 3L, TimeUnit.MINUTES);
                    return null;
                }
                template.opsForValue().set(SystemConstants.REDIS_CACHE + id, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                releaseLock(lockKey);
            }
            return shop;
        }
        // 在缓存之中
        // 1. 空对象
        if (shopJson.equals("")) {
            return null;
        }
        return JSONUtil.toBean(shopJson, Shop.class);
    }

    public Shop queryWithPassThrough(Long id) {
        String shopJson = template.opsForValue().get(SystemConstants.REDIS_CACHE + id);
        if (shopJson == null) {
            // 说明不存在于Cache之中
            Shop shop = getById(id);
            if (shop == null) {
                // 说明也不存在于数据库之中,那么在Cache中设置一个空字符串
                template.opsForValue().set(SystemConstants.REDIS_CACHE + String.valueOf(id), "", 3L, TimeUnit.MINUTES);
                return null;
            }
            template.opsForValue().set(SystemConstants.REDIS_CACHE + id, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
            return shop;
        }
        // 在缓存之中
        // 1. 空对象
        if (shopJson.equals(""))
            return null;
        // 2. 不是空对象
        return JSONUtil.toBean(shopJson, Shop.class);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
        String shopJson = template.opsForValue().get(SystemConstants.REDIS_CACHE + id);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        System.out.println(shopJson);
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
            // 如果没有过期直接返回对象
        }
        String lockKey = "shop:lock" + id;
        boolean triedLock = tryLock(lockKey);
        if (triedLock) {
            // 新建独立线程进行重建任务
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    releaseLock(lockKey);
                }

            });
        }
        return shop;

    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入redis
        template.opsForValue().set(SystemConstants.REDIS_CACHE + id, JSONUtil.toJsonStr(redisData));
    }


    private boolean tryLock(String key) {
        Boolean block = template.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(block);
    }

    private boolean releaseLock(String key) {
        return Boolean.TRUE.equals(template.delete(key));
    }
}
