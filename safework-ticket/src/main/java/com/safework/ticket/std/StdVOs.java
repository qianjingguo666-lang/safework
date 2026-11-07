package com.safework.ticket.std;

import com.safework.ticket.entity.StdAssessment;
import com.safework.ticket.entity.StdItem;

import java.util.List;

/** 标准化自评的返回对象 */
public final class StdVOs {

    private StdVOs() {
    }

    /** 列表一行：扣分项数、转了整改的项数 */
    public record ListRow(StdAssessment assessment, int deductCount, int hazardCount) {
    }

    public record ItemRow(StdItem item, List<String> evidence) {
    }

    /**
     * 详情
     *
     * @param score       得分（自评中按当前扣分实时算，完成后是保存的得分）
     * @param previous    上一次已完成自评的要素得分率（对比用），没有为 null
     * @param noteMissing 扣了分但没写说明的项数（完成前要补齐）
     */
    public record Detail(StdAssessment assessment, Double score, String grade, int dead, int serious,
                         List<StdRules.ElementScore> elements, String previousTitle, List<StdRules.ElementScore> previous,
                         List<ItemRow> items, boolean canEdit, int deductCount, int noteMissing) {
    }

    public record HazardResult(Long hazardId, String hazardNo) {
    }
}
