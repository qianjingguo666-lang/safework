package com.safework.ai.agent.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.dto.HazardActionDTO;
import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardAction;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.service.HazardService;
import com.safework.system.dto.UserOptionVO;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 指派隐患整改：选定整改责任人和期限（和页面上的【指派】按钮调用同一个方法） */
@Component
@RequiredArgsConstructor
public class AssignHazardAction implements AgentAction {

    private final HazardMapper hazardMapper;
    private final HazardService hazardService;
    private final UserService userService;

    @Override
    public String name() {
        return "assign_hazard";
    }

    @Override
    public String label() {
        return "指派隐患整改";
    }

    @Override
    public String description() {
        return "给【待指派】状态的隐患指定整改责任人和整改期限。需要隐患编号（不知道编号时先用 search_hazards 查询）、"
                + "整改人姓名、整改期限（根据当前时间换算成具体日期）。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .string("hazardNo", "隐患编号，例如 YH20261020-0001")
                .string("rectifierName", "整改责任人姓名")
                .string("deadline", "整改期限，格式 yyyy-MM-dd HH:mm，只给日期时默认当天 18:00")
                .string("comment", "给整改人的说明（可选）")
                .required("hazardNo", "rectifierName", "deadline")
                .build();
    }

    @Override
    public String permission() {
        return HazardService.PERM_HANDLE;
    }

    @Override
    public Proposal propose(JsonNode args) {
        Hazard h = HazardLookup.byNo(hazardMapper, ToolArgs.str(args, "hazardNo"));
        if (!HazardAction.ASSIGN.canFire(h.getStatus())) {
            throw new IllegalArgumentException("隐患 " + h.getHazardNo() + " 当前状态是【" + h.getStatus().getLabel() + "】，只有【待指派】的隐患可以指派");
        }
        UserOptionVO rectifier = ToolArgs.matchOne(userService.options(), UserOptionVO::getNickname,
                ToolArgs.str(args, "rectifierName"), "员工");
        LocalDateTime deadline = ToolArgs.dateTime(ToolArgs.str(args, "deadline"));
        if (deadline == null) {
            throw new IllegalArgumentException("请提供整改期限");
        }
        if (!deadline.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("整改期限 " + ToolArgs.time(deadline) + " 已经过去了，请换一个以后的时间");
        }
        if (deadline.isAfter(LocalDateTime.now().plusDays(90))) {
            throw new IllegalArgumentException("整改期限不能超过 90 天");
        }
        String comment = ToolArgs.str(args, "comment");
        if (comment != null && comment.length() > 200) {
            comment = comment.substring(0, 200);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("hazardId", h.getId());
        params.put("rectifierId", rectifier.getId());
        params.put("deadline", ToolArgs.time(deadline));
        params.put("comment", comment);
        List<ActionCard.Field> fields = new ArrayList<>();
        fields.add(new ActionCard.Field("隐患", h.getHazardNo() + " " + h.getTitle()));
        fields.add(new ActionCard.Field("等级 / 位置", ("MAJOR".equals(h.getLevel()) ? "重大隐患" : "一般隐患") + " · " + h.getLocation()));
        fields.add(new ActionCard.Field("整改责任人", rectifier.getNickname() + (rectifier.getDeptName() == null ? "" : "（" + rectifier.getDeptName() + "）")));
        fields.add(new ActionCard.Field("整改期限", ToolArgs.time(deadline)));
        if (comment != null) {
            fields.add(new ActionCard.Field("说明", comment));
        }
        return new Proposal(params, fields, "确认后隐患进入【整改中】，并通知整改责任人");
    }

    @Override
    public String execute(Map<String, Object> params) {
        HazardActionDTO dto = new HazardActionDTO();
        dto.setRectifierId(PendingActionService.longOf(params, "rectifierId"));
        dto.setDeadline(ToolArgs.dateTime((String) params.get("deadline")));
        dto.setComment((String) params.get("comment"));
        Long hazardId = PendingActionService.longOf(params, "hazardId");
        // 和页面上点【指派】完全一样：状态机、数据校验、操作记录、消息通知都会执行
        hazardService.assign(hazardId, dto);
        Hazard h = hazardMapper.selectById(hazardId);
        return "已指派 " + h.getRectifierName() + " 整改【" + h.getHazardNo() + " " + h.getTitle() + "】，期限 "
                + ToolArgs.time(h.getDeadline()) + "，已通知整改人";
    }
}
