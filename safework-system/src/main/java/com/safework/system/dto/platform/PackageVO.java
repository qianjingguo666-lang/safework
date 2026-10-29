package com.safework.system.dto.platform;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PackageVO {
    private Long id;
    private String packageName;
    private List<Long> menuIds;
    /** 包含的页面数量，列表上展示 */
    private int pageCount;
    private Integer sort;
    private Integer status;
    private String remark;
    /** 正在使用这个套餐的企业数 */
    private long tenantCount;
    private LocalDateTime createTime;
}
