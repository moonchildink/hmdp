package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.annotation.Resources;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    //    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 获取消息队列中的订单信息 XREADGROUP g1 c1  COUNT 1 BLOCK 2000  STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> read = template.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofMillis(2000)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2. 判断消息是否获取成功
                    if (read == null || read.isEmpty())
                        continue;
                    // 获取失败死循环
                    // 如果获取成功开始处理
                    MapRecord<String, Object, Object> entries = read.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    //      ack确认
                    template.opsForStream().acknowledge(
                            queueName, "g1", entries.getId()
                    );
                    //      失败的话在异常之中进行处理
                } catch (Exception e) {
                    handlePendingList();

                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> read = template.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2. 判断消息是否获取成功
                    if (read == null || read.isEmpty())
                        break;
                    // 获取失败死循环
                    // 如果获取成功开始处理
                    MapRecord<String, Object, Object> entries = read.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    //      ack确认
                    template.opsForStream().acknowledge(
                            queueName, "g1", entries.getId()
                    );
                } catch (Exception e) {
                    log.error("PendingList 订单处理异常：", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            Long userId = voucherOrder.getUserId();
            RLock lock = redissonClient.getLock("lock:order:" + userId);


            // 不传参数的情况下不等待，可以设置等待时间。
            boolean isLock = lock.tryLock();
            if (!isLock) {
                log.error("不允许重复下单");
                return;
            }
            try {
                // Transactional
                proxy.createVoucherOrder(voucherOrder);
            } catch (Exception e) {
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
        Long userId = UserHolder.getUser().getId();

        long orderId = redisIdWorker.nextId("order");
        Long result = template.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int i = result.intValue();
        if (i != 0)
            return Result.fail(i == 1 ? "库存不足" : "不能重复下单");
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);


    }


    /**
     * 使用JVM阻塞队列处理订单
     * Long id = UserHolder.getUser().getId();
     * <p>
     * // TODO：如何处理Lua操作错误？
     * // TODO：分布式锁操作机制还需要仔细考虑
     * Long result = template.execute(
     * SECKILL_SCRIPT,
     * Collections.emptyList(),
     * voucherId.toString(),
     * id.toString()
     * );
     * // 判断结果为0：表示可以购买
     * int i = result.intValue();
     * if (i != 0) {
     * // 返回错误信息
     * return Result.fail(i == 1 ? "库存不足" : "不能重复下单");
     * }
     * <p>
     * VoucherOrder order = new VoucherOrder();
     * long orderId = redisIdWorker.nextId("order");
     * order.setId(orderId);
     * order.setUserId(id);
     * order.setVoucherId(voucherId);
     * orderTasks.add(order);
     * <p>
     * proxy = (IVoucherOrderService) AopContext.currentProxy();
     * <p>
     * return Result.ok(orderId);
     *
     * @param order
     */

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
