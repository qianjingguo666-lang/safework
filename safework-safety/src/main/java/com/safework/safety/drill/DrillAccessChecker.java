package com.safework.safety.drill;

import com.safework.common.exception.BizException;
import com.safework.system.file.BizAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 演练现场照片：能看演练的人（预案查看权限或参演人员）能看；有演练编辑权限的人能上传、删除；只能传图片 */
@Component
@RequiredArgsConstructor
public class DrillAccessChecker implements BizAccessChecker {

    private final DrillService drillService;

    @Override
    public String bizType() {
        return DrillService.BIZ_TYPE;
    }

    @Override
    public void checkView(Long bizId) {
        if (!drillService.canView(bizId)) {
            throw new BizException(404, "演练不存在");
        }
    }

    @Override
    public boolean canEdit(Long bizId, String tag) {
        checkView(bizId);
        return DrillService.TAG_PHOTO.equals(tag) && drillService.canEditPhotos(bizId);
    }

    @Override
    public boolean imageOnly(String tag) {
        return true;
    }
}
