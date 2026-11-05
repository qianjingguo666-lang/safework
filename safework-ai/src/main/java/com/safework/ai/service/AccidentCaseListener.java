package com.safework.ai.service;

import com.safework.safety.accident.AccidentVOs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 事故调查结案后，把事故案例写进 AI 知识库：以后问 AI “受限空间作业要注意什么”，能引用本企业发生过的事故。
 * <p>
 * 在结案事务提交之后执行（结案失败就不会写）；用新事务，因为提交后的回调里原事务已经结束。
 * 写知识库失败（比如向量接口超时）只记日志，不影响结案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccidentCaseListener {

    private final KnowledgeService knowledgeService;
    private final PlatformTransactionManager txManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClosed(AccidentVOs.ClosedEvent e) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            tx.executeWithoutResult(s -> knowledgeService.addText(e.title(), e.text()));
            log.info("事故调查 {} 结案，事故案例已写进知识库", e.investigationId());
        } catch (Exception ex) {
            log.warn("事故调查 {} 的案例写进知识库失败", e.investigationId(), ex);
        }
    }
}
