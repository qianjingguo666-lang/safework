package com.safework.safety.meeting;

import com.safework.safety.entity.MeetingAttendee;
import com.safework.safety.entity.MeetingTeam;
import com.safework.safety.entity.MeetingTeamMember;
import com.safework.safety.entity.ShiftMeeting;

import java.time.LocalDate;
import java.util.List;

/** 班前会的返回对象 */
public final class MeetingVOs {

    private MeetingVOs() {
    }

    /**
     * 班组 + 最近 30 天开展情况
     *
     * @param held30   最近 30 天开了班前会的天数
     * @param rate30   开展率（%）
     * @param today    今天开了没有
     * @param canHold  当前用户能不能给这个班组开班前会（班组长或管理员）
     */
    public record Team(MeetingTeam team, List<MeetingTeamMember> members, int held30, Integer rate30, boolean today,
                       LocalDate lastDate, boolean canHold) {
    }

    public record Detail(ShiftMeeting meeting, List<MeetingAttendee> attendees, boolean canConfirm, boolean canEdit) {
    }

    public record Page(List<ShiftMeeting> records, long total) {
    }

    public record Overview(int teams, int heldToday, int unfitToday, Integer rate30, int unconfirmedToday) {
    }

    /** 我要确认的班前会（手机端） */
    public record Pending(ShiftMeeting meeting, MeetingAttendee me) {
    }

    /** 手机端：我当班组长的班组今天的情况 + 我要确认的 */
    public record Mine(List<Team> leading, List<Pending> pending) {
    }
}
