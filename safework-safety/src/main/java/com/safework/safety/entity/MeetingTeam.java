package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 班组：班组长负责开班前会 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_team")
public class MeetingTeam extends BaseEntity {
    private String name;
    private Long leaderId;
    private String leaderName;
    private Integer status;
    private String remark;
}
