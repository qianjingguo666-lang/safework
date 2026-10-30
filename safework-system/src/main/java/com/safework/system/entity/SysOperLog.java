package com.safework.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志。tenantId 显式写入：租户插件发现 INSERT 语句里已经有 tenant_id 列时不会重复添加。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String module;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String requestMethod;
    private String requestUri;
    private String method;
    private String requestParams;
    /** 1 成功 0 失败 */
    private Integer status;
    private String errorMsg;
    private Long costMs;
    private String ip;
    /** 请求链路编号：和后端日志、接口错误提示里的编号一致 */
    private String traceId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
