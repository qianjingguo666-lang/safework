package com.safework.safety.contractor;

import com.safework.safety.entity.Contractor;
import com.safework.safety.entity.ContractorViolation;
import com.safework.safety.entity.ContractorWorker;

import java.util.List;

/**
 * @param actions 当前用户能做的状态操作：APPROVE / REJECT / RESUBMIT / BLACKLIST / LIFT
 */
public record ContractorDetailVO(Contractor contractor, List<ContractorWorker> workers, List<ContractorViolation> violations,
                                 List<String> actions) {
}
