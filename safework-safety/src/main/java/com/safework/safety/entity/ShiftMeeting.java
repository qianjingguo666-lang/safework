package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 班前会记录 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shift_meeting")
public class ShiftMeeting extends BaseEntity {
    private Long teamId;
    private String teamName;
    private LocalDate meetingDate;
    /** DAY 白班 / MIDDLE 中班 / NIGHT 夜班 */
    private String shift;
    private Long hostId;
    private String hostName;
    /** 安全要点 */
    private String topics;
    /** 安全要点是不是 AI 生成的（班组长可以修改后再保存） */
    private Integer aiUsed;
    private Integer memberCount;
    private Integer presentCount;
    /** 出席但状态不好、不能上岗的人数 */
    private Integer unfitCount;

    /** 已确认人数（查询时算） */
    @TableField(exist = false)
    private Integer confirmedCount;
}
