package com.safework.safety.cert;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.system.file.BizAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 证书扫描件的权限：能看证书的人能看扫描件（本人或安全员）；只有安全员能上传、删除
 */
@Component
@RequiredArgsConstructor
public class CertAccessChecker implements BizAccessChecker {

    public static final String TAG_SCAN = "SCAN";

    private final CertService certService;

    @Override
    public String bizType() {
        return CertService.BIZ_TYPE;
    }

    @Override
    public void checkView(Long bizId) {
        certService.detail(bizId);
    }

    @Override
    public boolean canEdit(Long bizId, String tag) {
        certService.detail(bizId);
        return TAG_SCAN.equals(tag) && StpUtil.hasPermission(CertService.PERM_EDIT);
    }

    @Override
    public boolean imageOnly(String tag) {
        return true;
    }
}
