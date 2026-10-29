package com.safework.system.file;

/**
 * 附件的访问权限跟随业务单据：能看隐患详情的人才能看它的照片，能处理当前环节的人才能上传。
 * 每种业务（隐患、作业票）在自己的模块里实现这个接口，附件模块不需要知道业务规则。
 */
public interface BizAccessChecker {

    /** 业务类型，如 HAZARD */
    String bizType();

    /** 没有查看权限时抛异常（和查看详情接口的规则一致，返回 404） */
    void checkView(Long bizId);

    /** 当前用户能不能给这张单据上传 / 删除这种用途的附件 */
    boolean canEdit(Long bizId, String tag);

    /** 这种用途是否只能传图片（如隐患现场照片） */
    default boolean imageOnly(String tag) {
        return false;
    }
}
