package com.safework.system.service;

import java.util.List;

/**
 * 开通新企业时自动创建的角色。作业票审批流按角色编码找人（如 guardian = 监护人），所以这几个编码要固定。
 * 菜单 ID 对应 init.sql 里的 sys_menu；实际写入时会和企业套餐取交集，套餐里没有的功能不会分配。
 */
final class PresetRoles {

    record Preset(String code, String name, int sort, String remark, List<Long> menuIds) {
    }

    static final List<Preset> ALL = List.of(
            new Preset("safety", "安全员", 2, "审批作业票，指派、验收隐患，管理知识库，查看数据看板",
                    List.of(2L, 200L, 2002L, 2003L, 2004L, 3L, 300L, 3003L, 301L, 3011L, 3012L, 3013L,
                            4L, 400L, 401L, 4011L, 4012L, 402L, 6L)),
            new Preset("leader", "作业负责人", 3, "申请作业票，上报隐患",
                    List.of(2L, 200L, 2001L, 3L, 300L, 301L, 3011L, 4L, 400L, 402L)),
            new Preset("guardian", "监护人", 4, "现场监护，查看作业票，上报隐患",
                    List.of(2L, 200L, 3L, 300L, 301L, 3011L, 4L, 400L, 402L))
    );

    private PresetRoles() {
    }
}
