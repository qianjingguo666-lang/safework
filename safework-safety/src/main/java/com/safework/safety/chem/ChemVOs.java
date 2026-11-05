package com.safework.safety.chem;

import com.safework.safety.entity.ChemFlow;
import com.safework.safety.entity.ChemItem;
import com.safework.safety.entity.ChemStore;

import java.math.BigDecimal;
import java.util.List;

/** 危险化学品的返回对象 */
public final class ChemVOs {

    private ChemVOs() {
    }

    /** 单元里的一种危化品 */
    public record StockRow(Long id, Long chemId, String chemName, String cas, String category, BigDecimal qty, BigDecimal maxQty,
                           BigDecimal criticalQty, boolean overstock, boolean hasMsds) {
    }

    /**
     * 存储单元 + 重大危险源辨识
     *
     * @param design  按设计最大储量辨识（正式结论）
     * @param current 按当前库存算（参考）
     * @param canFlow 当前用户能不能办理出入库（管理员或单元负责人）
     */
    public record Store(ChemStore store, List<StockRow> stocks, ChemRules.Result design, ChemRules.Result current,
                        int overstock, boolean canFlow) {
    }

    public record Overview(int chemicals, int stores, int major, int level1, int level2, int level3, int level4,
                           int overstock, int noMsds) {
    }

    /** 目录一行：危化品 + 存放在几个单元、总库存 */
    public record ItemRow(ChemItem item, int storeCount, BigDecimal totalQty, boolean hasMsds) {
    }

    public record FlowPage(List<ChemFlow> records, long total) {
    }

    /** 出入库结果：变动后库存、是否超过最大储量 */
    public record FlowResult(BigDecimal balance, boolean overstock) {
    }
}
