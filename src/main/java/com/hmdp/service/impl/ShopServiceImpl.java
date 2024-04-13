package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

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
        Shop shop = cacheClient.queryWithPassThrough(SystemConstants.LOGIN_TOKEN_TIME, TimeUnit.SECONDS, SystemConstants.REDIS_CACHE, id, Shop.class, this::getById);

        if (shop == null)
            return Result.fail("指定资源不存在");
        return Result.ok(shop);
    }

}
