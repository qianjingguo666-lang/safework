package com.safework.safety.contractor;

import java.util.List;

/** 新建作业票时选择承包商和作业人员 */
public record ContractorOptionVO(Long id, String name, List<Worker> workers) {

    public record Worker(Long id, String name, String jobType) {
    }
}
