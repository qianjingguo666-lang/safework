package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.chem.ChemRules;
import com.safework.safety.chem.ChemService;
import com.safework.safety.chem.ChemVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询危险化学品：各存储单元的库存、超储、重大危险源辨识结果 */
@Component
@RequiredArgsConstructor
public class ChemSearchTool implements AgentTool {

    private final ChemService chemService;

    @Override
    public String name() {
        return "search_chemicals";
    }

    @Override
    public String label() {
        return "查询危险化学品";
    }

    @Override
    public String description() {
        return "查询本企业危险化学品的存储情况：每个存储单元（罐区、仓库、生产装置）里各种危化品的当前库存、设计最大储量（单位吨）、是否超储，"
                + "以及按 GB 18218 辨识的重大危险源结果（S 值、R 值、一~四级）。"
                + "问“罐区现在甲醇有多少”“哪些危化品超储了”“我们有几处重大危险源、是几级”时使用。"
                + "wholePlant 是全厂汇总（不受 keyword 过滤影响），回答全厂有几处重大危险源时用它。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .string("keyword", "按危化品名称、CAS 号或存储单元名称过滤，例如“甲醇”“罐区”")
                .bool("overstockOnly", "只看超过设计最大储量的")
                .build();
    }

    @Override
    public String permission() {
        return ChemService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        String kw = ToolArgs.str(args, "keyword");
        boolean overOnly = Boolean.TRUE.equals(ToolArgs.bool(args, "overstockOnly"));
        int major = 0;
        int over = 0;
        List<ChemVOs.Store> all = chemService.stores().stream().filter(s -> Integer.valueOf(1).equals(s.store().getStatus())).toList();
        List<Map<String, Object>> stores = new ArrayList<>();
        for (ChemVOs.Store s : all) {
            boolean storeHit = kw != null && s.store().getName().contains(kw);
            List<Map<String, Object>> stocks = s.stocks().stream()
                    .filter(r -> !overOnly || r.overstock())
                    .filter(r -> kw == null || storeHit || r.chemName().contains(kw) || r.cas().contains(kw))
                    .map(r -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("chemical", r.chemName());
                        m.put("cas", r.cas());
                        m.put("qtyTon", plain(r.qty()));
                        m.put("maxQtyTon", plain(r.maxQty()));
                        m.put("overstock", r.overstock());
                        m.put("criticalQtyTon", r.criticalQty() == null ? "不参与辨识" : plain(r.criticalQty()));
                        return m;
                    }).toList();
            if (stocks.isEmpty()) {
                continue;
            }
            if (s.design().major()) {
                major++;
            }
            over += s.overstock();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("store", s.store().getName());
            m.put("location", s.store().getLocation());
            m.put("manager", s.store().getManagerName());
            m.put("majorHazard", s.design().major() ? ChemRules.LEVELS.get(s.design().level()) + "重大危险源" : "不构成重大危险源");
            m.put("S", plain(s.design().sum()));
            m.put("R", plain(s.design().r()));
            m.put("stocks", stocks);
            stores.add(m);
        }
        // 不管按什么关键词过滤，都给出全厂汇总，避免“只查了罐区就说只有 1 处重大危险源”
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("storeCount", all.size());
        summary.put("majorHazardCount", all.stream().filter(s -> s.design().major()).count());
        summary.put("majorHazards", all.stream().filter(s -> s.design().major())
                .map(s -> s.store().getName() + "（" + ChemRules.LEVELS.get(s.design().level()) + "）").toList());
        summary.put("overstockItems", all.stream().mapToInt(ChemVOs.Store::overstock).sum());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wholePlant", summary);
        data.put("stores", stores);
        data.put("note", "S、R 按设计最大储量计算；S ≥ 1 构成重大危险源；R ≥ 100 一级、≥ 50 二级、≥ 10 三级、< 10 四级");
        return new ToolResult(data, "匹配 " + stores.size() + " 个单元（重大危险源 " + major + " 处、超储 " + over + " 项）；全厂重大危险源 "
                + summary.get("majorHazardCount") + " 处");
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }
}
