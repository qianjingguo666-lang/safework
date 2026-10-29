package com.safework.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库文档（按租户隔离：每个企业维护自己的制度文件） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_document")
public class AiDocument extends BaseEntity {
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    private String title;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer charCount;
    private Integer chunkCount;
    /** 1 已生成向量 0 未生成（只能关键词检索） */
    private Integer embedded;
    private String status;
    private String errorMsg;
    private Long uploaderId;
    private String uploaderName;
}
