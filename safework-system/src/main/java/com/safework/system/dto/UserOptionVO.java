package com.safework.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 下拉选择用：只返回必要字段 */
@Data
@AllArgsConstructor
public class UserOptionVO {
    private Long id;
    private String nickname;
    private String deptName;
}
