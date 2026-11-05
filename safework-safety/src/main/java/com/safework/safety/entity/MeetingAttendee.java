package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 班前会点名：出勤、身体精神状态、本人确认 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_attendee")
public class MeetingAttendee extends BaseEntity {
    private Long meetingId;
    private Long userId;
    private String userName;
    /** PRESENT 出席 / LEAVE 请假 / ABSENT 缺席 */
    private String attend;
    /** GOOD 良好 / UNWELL 身体不适 / TIRED 精神疲劳 / DRINK 饮酒；不出席为空 */
    private String bodyState;
    private String note;
    /** 本人在手机上确认“已参加、清楚今天的安全要点” */
    private LocalDateTime confirmTime;
}
