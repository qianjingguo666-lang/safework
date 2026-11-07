package com.safework.safety.point;

import com.safework.safety.enums.HazardAction;
import com.safework.safety.service.HazardFiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 隐患确认、按期整改后加积分。在隐患事务提交之后执行（隐患操作失败就不会加分）；
 * 用新事务，加分失败只记日志，不影响隐患流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HazardPointListener {

    private final PointService pointService;
    private final PlatformTransactionManager txManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFired(HazardFiredEvent e) {
        if (e.action() != HazardAction.ASSIGN && e.action() != HazardAction.VERIFY_PASS) {
            return;
        }
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            tx.executeWithoutResult(s -> pointService.onHazard(e));
        } catch (Exception ex) {
            log.warn("隐患 {} {} 后加积分失败", e.hazardNo(), e.action(), ex);
        }
    }
}
