package com.safework.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志。tenantId 显式写入（登录时还没有租户上下文）；企业编码不存在时为 -1。
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

    public static final Long UNKNOWN_TENANT = -1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String tenantCode;
    private Long userId;
    private String username;
    private String ip;
    private String browser;
    private String os;
    /** 1 成功 0 失败 */
    private Integer status;
    private String msg;
    private LocalDateTime loginTime;
}
