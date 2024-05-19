package com.hmdp.mq;


import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.channels.Channel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
//@RabbitListener(queues = SystemConstants.SECKILL_MQ_QUEUE, ackMode = "MANUAL")
@Component
public class SeckillMessageListener {
    @Resource
    VoucherOrderServiceImpl service;

    private final ExecutorService threadPool = Executors.newFixedThreadPool(16);

//    @RabbitHandler
//    public void receiveSeckillOrder(@Payload VoucherOrder order, Channel channel, Message message) {
//        log.info("接收到订单消息:" + order);
//        threadPool.submit(() -> {
//            try {
//                service.createVoucherOrder(order);
//            }catch (Exception e){
//                log.warn("订单处理异常,正在自动重试");
//                try
//            }
//        })
//
//    }


}
