package com.safework.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 问答记录：用于追溯回答依据、统计没命中的问题来补充知识库 */
@Data
@TableName("ai_chat_log")
public class AiChatLog {
    public static final String CHAT = "CHAT";
    public static final String REVIEW = "REVIEW";
    public static final String AGENT = "AGENT";
    public static final String QUESTION = "QUESTION";
    /** 语音转文字（讯飞语音听写） */
    public static final String ASR = "ASR";
    /** 一句话提取隐患 */
    public static final String HAZARD = "HAZARD";
    /** 应急处置建议 */
    public static final String EMERGENCY = "EMERGENCY";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String bizType;
    private Long userId;
    private String userName;
    private String question;
    private String answer;
    private String sources;
    /** 1 知识库命中 0 未命中 */
    private Integer hit;
    private String model;
    private Integer status;
    private String errorMsg;
    private Long costMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
