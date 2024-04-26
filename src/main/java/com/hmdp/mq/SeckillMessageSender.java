//package com.hmdp.mq;
//
//import cn.hutool.json.JSONUtil;
//import com.hmdp.config.RabbitmqConfig;
//import com.hmdp.entity.VoucherOrder;
//import com.hmdp.utils.SystemConstants;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.connection.CorrelationData;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.Resource;
//
//@Component
//@Slf4j
//public class SeckillMessageSender {
//    @Resource
//    RabbitTemplate template;
//
//
//    public void sendVoucherOrder(VoucherOrder order) {
//        String jsonStr = JSONUtil.toJsonStr(order);
//
//        log.info("发送订单：" + jsonStr);
//        template.setConfirmCallback(((correlationData, ack, cause) -> {
//            if (!ack) {
//                // 如果发送失败那么再发一次
//                log.error("订单发送失败:" + jsonStr);
//                template.convertAndSend(SystemConstants.SECKILL_MQ_EXCHANGE,
//                        SystemConstants.SECKILL_MQ_ROUTINGKEY,
//                        order,
//                        new CorrelationData(order.getId().toString()));
//            }
//
//        }));
//
//        template.setMandatory(true);
//        template.setReturnsCallback((message, replyCode, replyText, exchange, routingKey) -> {
//            log.error("交换机发送消息到队列失败，错误原因："+replyText+"，执行将消息退回到 publisher 操作。"));
//            template.convertAndSend(SystemConstants.SECKILL_MQ_EXCHANGE,
//                    SystemConstants.SECKILL_MQ_ROUTINGKEY,
//                    order,
//                    new CorrelationData(order.getId().toString()));
//        });
//    }
//
//}
