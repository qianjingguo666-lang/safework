package com.safework.system.file;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FileListVO {
    private List<SysFile> files;
    /** 当前用户能不能上传（由业务规则决定，前端据此显示上传按钮） */
    private boolean editable;
    private int maxCount;
}
