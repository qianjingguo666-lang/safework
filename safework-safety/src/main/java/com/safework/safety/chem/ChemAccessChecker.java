package com.safework.safety.chem;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.common.exception.BizException;
import com.safework.system.file.BizAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MSDS（化学品安全技术说明书）附件：能看危化品的人都能看（现场人员要随时能查）；只有管理员能上传、删除
 */
@Component
@RequiredArgsConstructor
public class ChemAccessChecker implements BizAccessChecker {

    private final ChemService chemService;

    @Override
    public String bizType() {
        return ChemService.BIZ_TYPE;
    }

    @Override
    public void checkView(Long bizId) {
        if (!StpUtil.hasPermission(ChemService.PERM_LIST)) {
            throw new BizException(404, "危化品不存在");
        }
        chemService.item(bizId);
    }

    @Override
    public boolean canEdit(Long bizId, String tag) {
        checkView(bizId);
        return ChemService.TAG_MSDS.equals(tag) && StpUtil.hasPermission(ChemService.PERM_EDIT);
    }
}
