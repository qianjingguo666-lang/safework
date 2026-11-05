package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 班组成员 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meeting_team_member")
public class MeetingTeamMember extends BaseEntity {
    private Long teamId;
    private Long userId;
    private String userName;
}
