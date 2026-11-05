package com.safework.safety.cost;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.CostExpense;
import com.safework.system.file.BizAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 支出凭证（发票、合同、验收单的照片或 PDF）：能看台账的人都能看；有登记权限的人能上传、删除，作废后不能再改 */
@Component
@RequiredArgsConstructor
public class CostAccessChecker implements BizAccessChecker {

    private final CostService costService;

    @Override
    public String bizType() {
        return CostService.BIZ_TYPE;
    }

    @Override
    public void checkView(Long bizId) {
        if (!StpUtil.hasPermission(CostService.PERM_LIST)) {
            throw new BizException(404, "支出记录不存在");
        }
        costService.expense(bizId);
    }

    @Override
    public boolean canEdit(Long bizId, String tag) {
        if (!StpUtil.hasPermission(CostService.PERM_LIST)) {
            throw new BizException(404, "支出记录不存在");
        }
        CostExpense e = costService.expense(bizId);
        return CostService.TAG_VOUCHER.equals(tag) && StpUtil.hasPermission(CostService.PERM_EDIT) && Integer.valueOf(1).equals(e.getStatus());
    }
}
