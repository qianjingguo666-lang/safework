package com.safework.safety.meeting;

import com.safework.common.exception.BizException;
import com.safework.system.file.BizAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 班前会现场照片：能看班前会的人能看；主持人或安全管理员能上传、删除；只能传图片 */
@Component
@RequiredArgsConstructor
public class MeetingAccessChecker implements BizAccessChecker {

    private final MeetingService meetingService;

    @Override
    public String bizType() {
        return MeetingService.BIZ_TYPE;
    }

    @Override
    public void checkView(Long bizId) {
        if (!meetingService.canView(bizId)) {
            throw new BizException(404, "班前会不存在");
        }
    }

    @Override
    public boolean canEdit(Long bizId, String tag) {
        checkView(bizId);
        return MeetingService.TAG_PHOTO.equals(tag) && meetingService.canEditPhotos(bizId);
    }

    @Override
    public boolean imageOnly(String tag) {
        return true;
    }
}
