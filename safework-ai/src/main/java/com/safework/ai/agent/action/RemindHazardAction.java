package com.safework.ai.agent.action;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolSchema;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.service.HazardNotifier;
import com.safework.safety.service.HazardService;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 催办：给整改中隐患的责任人发一条待办消息 */
@Component
@RequiredArgsConstructor
public class RemindHazardAction implements AgentAction {

    private static final String DEFAULT_MESSAGE = "请尽快完成整改并在系统中提交";

    private final HazardMapper hazardMapper;
    private final MessageService messageService;

    @Override
    public String name() {
        return "remind_hazard_rectifier";
    }

    @Override
    public String label() {
        return "催办隐患整改";
    }

    @Override
    public String description() {
        return "给【整改中】隐患的整改责任人发送催办消息（站内消息，实时推送）。需要隐患编号，不知道时先用 search_hazards 查询；"
                + "用户要求催办多条隐患时，每条分别调用一次。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .string("hazardNo", "隐患编号")
                .string("message", "催办内容（可选，不超过 200 字），不填使用默认内容")
                .required("hazardNo")
                .build();
    }

    @Override
    public String permission() {
        return HazardService.PERM_HANDLE;
    }

    @Override
    public Proposal propose(JsonNode args) {
        Hazard h = HazardLookup.byNo(hazardMapper, ToolArgs.str(args, "hazardNo"));
        if (h.getStatus() != HazardStatus.RECTIFYING || h.getRectifierId() == null) {
            throw new IllegalArgumentException("隐患 " + h.getHazardNo() + " 当前状态是【" + h.getStatus().getLabel() + "】，只有【整改中】的隐患可以催办");
        }
        String message = ToolArgs.str(args, "message");
        if (message == null) {
            message = DEFAULT_MESSAGE;
        }
        if (message.length() > 200) {
            message = message.substring(0, 200);
        }
        String deadline = ToolArgs.time(h.getDeadline());
        if (h.getDeadline() != null && h.getDeadline().isBefore(LocalDateTime.now())) {
            deadline += "（已逾期 " + Math.max(1, Duration.between(h.getDeadline(), LocalDateTime.now()).toDays()) + " 天）";
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("hazardId", h.getId());
        params.put("message", message);
        List<ActionCard.Field> fields = new ArrayList<>();
        fields.add(new ActionCard.Field("隐患", h.getHazardNo() + " " + h.getTitle()));
        fields.add(new ActionCard.Field("整改责任人", h.getRectifierName()));
        fields.add(new ActionCard.Field("整改期限", deadline));
        fields.add(new ActionCard.Field("催办内容", message));
        return new Proposal(params, fields, "确认后整改责任人会立即收到一条待办消息");
    }

    @Override
    public String execute(Map<String, Object> params) {
        Hazard h = hazardMapper.selectById(PendingActionService.longOf(params, "hazardId"));
        if (h == null || h.getStatus() != HazardStatus.RECTIFYING) {
            throw new BizException("隐患已经不在【整改中】状态，不需要催办了");
        }
        Object me = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
        String deadline = h.getDeadline() == null ? "" : "（整改期限 " + ToolArgs.time(h.getDeadline()) + "）";
        messageService.send(List.of(h.getRectifierId()), MessageDraft.of(MessageCategory.TODO, HazardNotifier.BIZ_TYPE, h.getId(),
                "催办：" + h.getHazardNo() + " " + h.getTitle(), me + "：" + params.get("message") + deadline, HazardNotifier.link(h.getId())));
        return "已向 " + h.getRectifierName() + " 发送催办消息";
    }
}
