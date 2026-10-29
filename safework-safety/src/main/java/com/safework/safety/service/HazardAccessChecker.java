package com.safework.safety.service;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardStatus;
import com.safework.system.file.BizAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 隐患照片的权限：
 * - 查看：能看隐患详情的人（安全员看全部，其他人只能看自己上报或负责整改的）
 * - 现场照片（REPORT）：待指派阶段，上报人或安全员可以上传
 * - 整改照片（RECTIFY）：整改中阶段，只有整改责任人可以上传
 */
@Component
@RequiredArgsConstructor
public class HazardAccessChecker implements BizAccessChecker {

    public static final String TAG_REPORT = "REPORT";
    public static final String TAG_RECTIFY = "RECTIFY";

    private final HazardService hazardService;

    @Override
    public String bizType() {
        return HazardNotifier.BIZ_TYPE;
    }

    @Override
    public void checkView(Long bizId) {
        hazardService.detail(bizId);
    }

    @Override
    public boolean canEdit(Long bizId, String tag) {
        Hazard h = hazardService.detail(bizId).getHazard();
        Long me = StpUtil.getLoginIdAsLong();
        if (TAG_REPORT.equals(tag)) {
            return h.getStatus() == HazardStatus.REPORTED
                    && (me.equals(h.getReporterId()) || StpUtil.hasPermission(HazardService.PERM_HANDLE));
        }
        if (TAG_RECTIFY.equals(tag)) {
            return h.getStatus() == HazardStatus.RECTIFYING && me.equals(h.getRectifierId());
        }
        return false;
    }

    @Override
    public boolean imageOnly(String tag) {
        return true;
    }
}
