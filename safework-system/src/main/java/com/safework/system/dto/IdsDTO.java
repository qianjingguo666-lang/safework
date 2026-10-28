package com.safework.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 分配角色 / 分配菜单时传入的 ID 列表，传空数组表示全部清空 */
@Data
public class IdsDTO {
    @NotNull(message = "ids 不能为空")
    private List<Long> ids;
}
