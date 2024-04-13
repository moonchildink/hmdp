package com.hmdp;

import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;

@SpringBootTest
public class HmDianPingApplicationTest {
    @Resource
    private IShopTypeService typeService;

    @Resource
    StringRedisTemplate template;

    @Test
    public void test() {
        System.out.println(template.opsForValue().get("wy"));

    }

    @Resource
    private ShopServiceImpl shopService;

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L,10L);
    }


}
