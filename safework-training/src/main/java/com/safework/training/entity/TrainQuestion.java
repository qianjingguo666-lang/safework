package com.safework.training.entity;

import com.safework.common.core.BaseEntity;
import com.safework.training.enums.QuestionType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 题库里的一道题 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("train_question")
public class TrainQuestion extends BaseEntity {
    private QuestionType type;
    /** 分类：受限空间作业、动火作业、高处作业、应急救援、通用安全等 */
    private String category;
    private String content;
    /** 选项，JSON 数组字符串，如 ["19.5%~23.5%","大于 18%"] */
    private String optionList;
    /** 正确答案字母，单选 "B"，多选 "ACD"，判断 "A"（正确）/"B"（错误） */
    private String answer;
    private String analysis;
    /** MANUAL 人工录入 / AI 生成 */
    private String source;
    /** AI 出题依据的制度文件 */
    private String sourceDoc;
    /** 0 待审核（AI 生成的题必须人工审核后才能用于考试）1 启用 2 停用 */
    private Integer status;
}
