package com.safework.ai.agent.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolSchema;
import com.safework.system.message.NoticeSaveDTO;
import com.safework.system.message.NoticeService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 发布通知公告：全体员工都会收到，所以内容必须由人看过再发 */
@Component
@RequiredArgsConstructor
public class PublishNoticeAction implements AgentAction {

    private final NoticeService noticeService;
    private final UserService userService;

    @Override
    public String name() {
        return "publish_notice";
    }

    @Override
    public String label() {
        return "发布通知公告";
    }

    @Override
    public String description() {
        return "发布企业通知公告，全体员工都会收到。可以根据查询到的数据起草内容（例如逾期隐患通报、安全提醒），"
                + "内容要客观、简洁，不要编造数据。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .string("title", "公告标题，不超过 50 字")
                .string("content", "公告正文，不超过 2000 字")
                .bool("important", "是否重要公告（标题前加【重要】）")
                .required("title", "content")
                .build();
    }

    @Override
    public String permission() {
        return "system:notice:add";
    }

    @Override
    public Proposal propose(JsonNode args) {
        String title = ToolArgs.str(args, "title");
        String content = ToolArgs.str(args, "content");
        if (title == null || content == null) {
            throw new IllegalArgumentException("公告标题和内容都不能为空");
        }
        if (title.length() > 50) {
            throw new IllegalArgumentException("标题不能超过 50 字，请精简");
        }
        if (content.length() > 2000) {
            throw new IllegalArgumentException("内容不能超过 2000 字，请精简");
        }
        boolean important = Boolean.TRUE.equals(ToolArgs.bool(args, "important"));
        int receivers = userService.listAllActiveIds().size();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", title);
        params.put("content", content);
        params.put("important", important);
        List<ActionCard.Field> fields = new ArrayList<>();
        fields.add(new ActionCard.Field("标题", (important ? "【重要】" : "") + title));
        fields.add(new ActionCard.Field("接收人", "全体员工（" + receivers + " 人）"));
        fields.add(new ActionCard.Field("内容", content));
        return new Proposal(params, fields, "内容由 AI 起草，请仔细核对后再发布；发布后可以在【通知公告】里撤回");
    }

    @Override
    public String execute(Map<String, Object> params) {
        NoticeSaveDTO dto = new NoticeSaveDTO();
        dto.setTitle((String) params.get("title"));
        dto.setContent((String) params.get("content"));
        dto.setLevel(Boolean.TRUE.equals(params.get("important")) ? "IMPORTANT" : "NORMAL");
        noticeService.publish(dto);
        return "公告【" + dto.getTitle() + "】已发布，已通知 " + userService.listAllActiveIds().size() + " 人";
    }
}
