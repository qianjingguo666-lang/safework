package com.safework.system.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PackageSaveDTO {
    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 50, message = "套餐名称最多 50 字")
    private String packageName;
    /** 勾选的菜单（含半选中的上级目录） */
    @NotNull(message = "请选择套餐包含的功能")
    private List<Long> menuIds;
    private Integer sort;
    @Size(max = 200, message = "备注最多 200 字")
    private String remark;
}
