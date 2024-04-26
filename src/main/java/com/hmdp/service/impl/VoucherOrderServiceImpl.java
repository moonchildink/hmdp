package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitmqConfig;
import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisLock;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.annotation.Resources;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker idWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate template;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                // 1. 获取订单信息
                try {
                    VoucherOrder order = orderTasks.take();
                    handleVoucherOrder(order);
                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
                    log.error("处理订单异常", e);
                }
                // 2. 创建订单
            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            Long userId = voucherOrder.getUserId();
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            boolean isLock = lock.tryLock();
            if (!isLock) {
                log.error("不允许重复下单");
                return;
            }
            try {
//                IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
                proxy.createVoucherOrder(voucherOrder);
            } catch (Exception e) {
//                throw new RuntimeException(e);
                log.error("订单购买失败：" + voucherOrder.getVoucherId());
            }

        }
    }

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private RedisIdWorker redisIdWorker;
    private IVoucherOrderService proxy;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean triedLock = lock.tryLock();
//        if (!triedLock) {
//            return Result.fail("重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }

        // 指定lua脚本，根据lua脚本的返回信息执行相应的操作
        Long id = UserHolder.getUser().getId();
        Long result = template.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                id.toString()
        );
        // 判断结果为0：表示可以购买
        int i = result.intValue();
        if (i != 0) {
            // 返回错误信息
            return Result.fail(i == 1 ? "库存不足" : "不能重复下单");
        }

        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        order.setUserId(id);
        order.setVoucherId(voucherId);
//        orderTasks.add(order);
//
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        return Result.ok(orderId);

        String rabbitQueue = SystemConstants.SECKILL_MQ_QUEUE;
        rabbitTemplate.convertAndSend(rabbitQueue, order);

        return Result.ok(orderId);

    }

    @Transactional
    public void createVoucherOrder(VoucherOrder order) {
//        Long userId = UserHolder.getUser().getId();
//        Long userId = order.getUserId();
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock -1 ")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!isSuccess)
            log.error("创建订单失败：" + order.getVoucherId());
//            return Result.fail("库存不足");
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = idWorker.nextId("order");
//
//        voucherOrder.setVoucherId(order.getVoucherId()).setId(orderId).setUserId(userId);
        save(order);
        log.debug("创建订单:" + order.getVoucherId() + ",用户:" + order.getUserId());
    }

//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        int count = Math.toIntExact(query().eq("user_id", userId).eq("voucher_id", voucherId).count());
//        if (count > 0)
//            return Result.fail("已经买过了");
//
//
//        boolean isSuccess = seckillVoucherService.update()
//                .setSql("stock = stock -1 ")
//                .eq("voucher_id", voucherId)
//                .gt("stock", 0)
//                .update();
//        if (!isSuccess)
//            return Result.fail("库存不足");
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = idWorker.nextId("order");
//
//        voucherOrder.setVoucherId(voucherId).setId(orderId).setUserId(userId);
//        save(voucherOrder);
//
//        return Result.ok(orderId);
//    }
}
