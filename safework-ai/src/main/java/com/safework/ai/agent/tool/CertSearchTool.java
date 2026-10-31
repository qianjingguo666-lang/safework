package com.safework.ai.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.cert.CertQueryDTO;
import com.safework.safety.cert.CertRules;
import com.safework.safety.cert.CertService;
import com.safework.safety.entity.PersonCert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询人员资质证书：谁有什么证、哪些快到期了 */
@Component
@RequiredArgsConstructor
public class CertSearchTool implements AgentTool {

    private final CertService certService;

    @Override
    public String name() {
        return "search_certs";
    }

    @Override
    public String label() {
        return "查询人员资质";
    }

    @Override
    public String description() {
        return "查询本企业人员的资质证书（特种作业操作证、有限空间培训合格证、监护人培训合格证等）：持证人、证书编号、有效期、复审日期、状态。"
                + "问“哪些证书快到期了”“某人有没有焊工证”“哪些人证书过期了”时使用。结果按到期时间从近到远排列。"
                + "普通员工只能查到自己的证书。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .enumOf("state", "证书状态：EXPIRING 30 天内到期（含复审）、EXPIRED 已过期或复审逾期、VALID 有效",
                        List.of(CertRules.EXPIRING, CertRules.EXPIRED, CertRules.VALID))
                .enumOf("certType", "证书类型：" + CertRules.CERT_TYPES, List.copyOf(CertRules.CERT_TYPES.keySet()))
                .string("keyword", "按持证人姓名或证书编号模糊搜索")
                .integer("limit", "返回明细的条数，默认 10", 1, 20)
                .build();
    }

    @Override
    public String permission() {
        return CertService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        CertQueryDTO q = new CertQueryDTO();
        q.setSize(ToolArgs.intIn(args, "limit", 10, 1, 20));
        q.setState(ToolArgs.oneOf(args, "state", CertRules.EXPIRING, CertRules.EXPIRED, CertRules.VALID));
        q.setCertType(ToolArgs.oneOf(args, "certType", CertRules.CERT_TYPES.keySet().toArray(String[]::new)));
        q.setKeyword(ToolArgs.str(args, "keyword"));
        Page<PersonCert> page = certService.page(q);
        List<Map<String, Object>> items = page.getRecords().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("holder", c.getUserName());
            m.put("dept", c.getDeptName());
            m.put("certType", CertRules.CERT_TYPES.getOrDefault(c.getCertType(), c.getCertType()));
            m.put("certNo", c.getCertNo());
            m.put("expireDate", c.getExpireDate() == null ? "长期" : c.getExpireDate().toString());
            m.put("reviewDate", c.getReviewDate() == null ? "无需复审" : c.getReviewDate().toString());
            m.put("state", switch (c.getState()) {
                case CertRules.EXPIRED -> "已过期";
                case CertRules.EXPIRING -> "即将到期";
                default -> "有效";
            });
            m.put("daysLeft", c.getDaysLeft());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.getTotal());
        data.put("items", items);
        return new ToolResult(data, "共 " + page.getTotal() + " 张，返回 " + items.size() + " 张");
    }
}
