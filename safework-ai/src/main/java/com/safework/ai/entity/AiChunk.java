package com.safework.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chunk")
public class AiChunk {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private Integer pageNo;
    private String content;
    /** float32 小端序 → Base64；接口不返回 */
    @JsonIgnore
    private String embedding;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
