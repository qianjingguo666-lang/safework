package com.safework.system.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 导入模板第二个工作表“填写说明” */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteRow {
    @ExcelProperty("填写说明")
    @ColumnWidth(90)
    private String text;
}
