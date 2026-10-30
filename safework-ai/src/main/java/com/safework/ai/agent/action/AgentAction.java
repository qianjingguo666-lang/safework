package com.safework.ai.agent.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * 智能体可以发起的写操作（人在回路）。
 * <p>
 * 和查询工具不同，大模型调用写操作时<b>不会直接执行</b>：
 * <ol>
 *   <li>{@link #propose}：校验参数、查出要操作的对象，生成一张“确认卡片”（操作什么、改成什么）；</li>
 *   <li>卡片推送给页面，用户核对后点击【确认执行】；</li>
 *   <li>{@link #execute}：再次检查权限，调用和页面完全相同的业务方法执行（状态机、数据校验、消息通知都会生效）。</li>
 * </ol>
 * 审批作业票、验收隐患这类需要现场核对的操作，故意不开放给智能体。
 */
public interface AgentAction {

    /** 函数名，例如 assign_hazard */
    String name();

    /** 中文名，例如“指派隐患整改” */
    String label();

    String description();

    ObjectNode parameters();

    /** 需要的权限码（生成卡片和确认执行时都会检查） */
    String permission();

    /**
     * 校验参数，生成操作预览。参数有问题抛 IllegalArgumentException，错误信息会返回给大模型。
     */
    Proposal propose(JsonNode args);

    /**
     * 用户确认后执行。
     *
     * @param params propose 时解析好的参数（已经是 ID 等确定的值，不会再让大模型参与）
     * @return 执行结果，显示在卡片上
     */
    String execute(Map<String, Object> params);

    /**
     * 操作预览
     *
     * @param params 执行时用的参数
     * @param fields 卡片上展示的内容（标签 → 值）
     * @param tip    提醒，例如“确认后会通知整改责任人”
     */
    record Proposal(Map<String, Object> params, List<ActionCard.Field> fields, String tip) {
    }
}
