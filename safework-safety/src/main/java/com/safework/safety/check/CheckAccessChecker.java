package com.safework.safety.check;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.safety.entity.CheckTask;
import com.safework.system.file.BizAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 检查现场照片的权限：能看任务的人能看照片（检查人、安全员）；只有检查人、并且在提交之前能上传和删除
 */
@Component
@RequiredArgsConstructor
public class CheckAccessChecker implements BizAccessChecker {

    public static final String TAG_SCENE = "SCENE";

    private final CheckService checkService;

    @Override
    public String bizType() {
        return CheckService.BIZ_TYPE;
    }

    @Override
    public void checkView(Long bizId) {
        checkService.detail(bizId);
    }

    @Override
    public boolean canEdit(Long bizId, String tag) {
        checkService.detail(bizId);
        CheckTask t = checkService.loadTask(bizId);
        return TAG_SCENE.equals(tag) && CheckRules.PENDING.equals(t.getStatus())
                && t.getCheckerId().equals(StpUtil.getLoginIdAsLong());
    }

    @Override
    public boolean imageOnly(String tag) {
        return true;
    }
}
