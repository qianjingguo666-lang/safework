package com.safework.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeptSaveDTO {
    /** 上级部门，0 或不填表示顶级 */
    private Long parentId;
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称不超过 50 字")
    private String deptName;
    private String leader;
    private String phone;
    private Integer sort;
    private Integer status;
}
