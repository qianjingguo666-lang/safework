package com.safework.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeptVO {
    private Long id;
    private Long parentId;
    private String deptName;
    private String leader;
    private String phone;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private List<DeptVO> children = new ArrayList<>();
}
