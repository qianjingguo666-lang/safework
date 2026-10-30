package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ai.dto.SourceVO;
import com.safework.ai.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 检索企业安全制度：复用知识库问答的混合检索（RAG 作为智能体的一个工具） */
@Component
@RequiredArgsConstructor
public class RegulationSearchTool implements AgentTool {

    private static final int MAX_CONTENT = 400;

    private final RetrievalService retrieval;

    @Override
    public String name() {
        return "search_regulations";
    }

    @Override
    public String label() {
        return "检索安全制度";
    }

    @Override
    public String description() {
        return "在企业上传的安全制度、作业规程中检索相关条款，返回最相关的原文片段和出处。"
                + "问到安全规定、作业要求、标准数值（如氧含量、检测时间）、应急处置时使用；回答时要注明出处。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .string("query", "检索内容，用简洁的关键词或短句，例如“受限空间 气体检测 合格标准”")
                .required("query")
                .build();
    }

    @Override
    public String permission() {
        return "ai:chat";
    }

    @Override
    public ToolResult execute(JsonNode args) {
        String query = ToolArgs.str(args, "query");
        if (query == null) {
            throw new IllegalArgumentException("缺少参数 query");
        }
        List<SourceVO> sources = retrieval.retrieve(query.length() > 200 ? query.substring(0, 200) : query, 4);
        List<Map<String, Object>> items = sources.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("document", s.documentTitle());
            m.put("page", s.pageNo());
            m.put("score", s.score());
            String c = s.content();
            m.put("content", c != null && c.length() > MAX_CONTENT ? c.substring(0, MAX_CONTENT) + "…" : c);
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        if (items.isEmpty()) {
            data.put("tip", "知识库中没有找到相关规定，不要编造");
        }
        return new ToolResult(data, items.isEmpty() ? "没有找到相关规定" : "找到 " + items.size() + " 段相关条款");
    }
}
