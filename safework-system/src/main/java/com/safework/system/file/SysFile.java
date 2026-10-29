package com.safework.system.file;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 附件（按企业隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends BaseEntity {
    private String bizType;
    private Long bizId;
    private String tag;
    private String originalName;
    private String ext;
    private String contentType;
    private Long size;
    /** 存储路径不返回给前端：下载只能走带权限校验的接口 */
    @JsonIgnore
    private String storageKey;
    private Long uploaderId;
    private String uploaderName;
}
