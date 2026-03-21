package com.loan.app.consumer;

import com.loan.app.config.RabbitConfig;
import com.loan.app.event.LoanOrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoanDlxConsumer {

    @RabbitListener(queues = RabbitConfig.DLX_QUEUE)
    public void handleDeadLetter(LoanOrderCreatedEvent event) {
        log.error("【报警】检测到无法处理的死信订单！订单号: {}, 金额: {}, 用户: {}",
                event.getOrderNo(), event.getAmount(), event.getUserId());
    }
}
