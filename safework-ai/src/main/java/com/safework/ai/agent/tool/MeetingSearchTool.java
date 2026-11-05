package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.entity.MeetingAttendee;
import com.safework.safety.entity.ShiftMeeting;
import com.safework.safety.meeting.MeetingDTOs;
import com.safework.safety.meeting.MeetingRules;
import com.safework.safety.meeting.MeetingService;
import com.safework.safety.meeting.MeetingVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询班前会：各班组今天开了没有、开展率、今天谁状态不好不能上岗 */
@Component
@RequiredArgsConstructor
public class MeetingSearchTool implements AgentTool {

    private final MeetingService meetingService;

    @Override
    public String name() {
        return "search_shift_meetings";
    }

    @Override
    public String label() {
        return "查询班前会";
    }

    @Override
    public String description() {
        return "查询班前会开展情况：每个班组今天开了没有、最近 30 天开了几天（开展率）、班组长是谁；"
                + "以及指定日期（默认今天）出席但身体或精神状态不好、不能上岗的人。"
                + "问“今天哪些班组没开班前会”“今天谁不能上岗”“罐区操作班最近班前会开得怎么样”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .string("date", "查哪一天不能上岗的人，格式 yyyy-MM-dd，默认今天")
                .build();
    }

    @Override
    public String permission() {
        return MeetingService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        String ds = ToolArgs.str(args, "date");
        LocalDate date;
        try {
            date = ds == null ? LocalDate.now() : LocalDate.parse(ds);
        } catch (Exception e) {
            throw new IllegalArgumentException("date 格式不对，应为 yyyy-MM-dd");
        }
        List<Map<String, Object>> teams = meetingService.teams().stream()
                .filter(t -> Integer.valueOf(1).equals(t.team().getStatus()))
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("team", t.team().getName());
                    m.put("leader", t.team().getLeaderName());
                    m.put("members", t.members().size());
                    m.put("heldToday", t.today());
                    m.put("last30Days", t.held30() + " 天（" + t.rate30() + "%）");
                    m.put("lastDate", t.lastDate() == null ? null : t.lastDate().toString());
                    return m;
                }).toList();
        MeetingDTOs.Query q = new MeetingDTOs.Query();
        q.setFrom(date);
        q.setTo(date);
        q.setUnfit(true);
        q.setSize(20);
        List<Map<String, Object>> unfit = new ArrayList<>();
        for (ShiftMeeting sm : meetingService.page(q).records()) {
            MeetingVOs.Detail d = meetingService.detail(sm.getId());
            for (MeetingAttendee a : d.attendees()) {
                if (MeetingRules.unfit(a.getAttend(), a.getBodyState())) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", a.getUserName());
                    m.put("team", sm.getTeamName());
                    m.put("shift", MeetingRules.SHIFTS.get(sm.getShift()));
                    m.put("state", MeetingRules.CONDITIONS.get(a.getBodyState()));
                    m.put("note", a.getNote());
                    unfit.add(m);
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("teams", teams);
        data.put("date", date.toString());
        data.put("unfitPeople", unfit);
        long notHeld = teams.stream().filter(t -> Boolean.FALSE.equals(t.get("heldToday"))).count();
        return new ToolResult(data, teams.size() + " 个班组，今天未开 " + notHeld + " 个；" + date + " 不能上岗 " + unfit.size() + " 人");
    }
}
