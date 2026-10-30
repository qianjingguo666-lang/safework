package com.safework.ai.agent.action;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.entity.Hazard;
import com.safework.safety.mapper.HazardMapper;

/** 按编号找隐患（租户插件保证只能找到本企业的） */
final class HazardLookup {

    private HazardLookup() {
    }

    static Hazard byNo(HazardMapper mapper, String hazardNo) {
        if (hazardNo == null) {
            throw new IllegalArgumentException("请提供隐患编号（可以先用 search_hazards 查询）");
        }
        Hazard h = mapper.selectOne(Wrappers.<Hazard>lambdaQuery().eq(Hazard::getHazardNo, hazardNo.trim()));
        if (h == null) {
            throw new IllegalArgumentException("没有找到编号为 " + hazardNo + " 的隐患，请先用 search_hazards 查询正确的编号");
        }
        return h;
    }
}
