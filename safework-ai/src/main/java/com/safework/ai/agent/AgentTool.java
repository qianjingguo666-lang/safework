package com.safework.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 智能体可以调用的一个工具（Function Calling 里的 function）。
 * <p>
 * 设计约定：
 * <ul>
 *   <li>只读：工具只做查询，不做新增、修改、删除。写操作必须由人在页面上确认，AI 不能代办；</li>
 *   <li>复用业务 Service：查询走和页面一样的 Service 方法，自动带上租户隔离和数据权限（比如监护人只能查到自己相关的作业票）；</li>
 *   <li>每个工具声明需要的权限码：用户没有这个权限，工具根本不会发给大模型，执行时还会再检查一次。</li>
 * </ul>
 */
public interface AgentTool {

    /** 函数名，发给大模型，只能用字母、数字、下划线，例如 search_hazards */
    String name();

    /** 中文名，显示在页面的“调用过程”里 */
    String label();

    /** 告诉大模型这个工具能做什么、什么时候用。写得越清楚，模型越不容易用错 */
    String description();

    /** 参数的 JSON Schema（用 {@link ToolSchema} 生成） */
    ObjectNode parameters();

    /** 需要的权限码 */
    String permission();

    /**
     * 执行。参数不合法时抛 IllegalArgumentException，错误信息会返回给大模型，让它修正参数后重试。
     */
    ToolResult execute(JsonNode args);
}
