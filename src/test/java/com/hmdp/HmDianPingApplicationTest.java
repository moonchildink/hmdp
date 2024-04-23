package com.hmdp;

import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    }


    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Resource
    RedisIdWorker worker;

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = worker.nextId("order");
                System.out.println("id=" + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time_assumption:" + (end - begin));

    }

    @Resource
    RedissonClient redissonClient;


    @Test
    void testNewRedissonClient() {
        RLock lock = redissonClient.getLock("lock:test");
        boolean isLock = lock.tryLock();
        System.out.println(lock);
        System.out.println(isLock);
    }
}
