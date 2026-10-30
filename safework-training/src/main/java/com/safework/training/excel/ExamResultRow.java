package com.safework.training.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/** 考试成绩导出（每人一行，取最高分） */
@Data
@HeadRowHeight(22)
@ColumnWidth(16)
public class ExamResultRow {
    @ExcelProperty("姓名")
    @ColumnWidth(20)
    private String userName;
    @ExcelProperty("部门")
    @ColumnWidth(20)
    private String deptName;
    @ExcelProperty("最高分")
    @ColumnWidth(10)
    private Integer score;
    @ExcelProperty("是否通过")
    @ColumnWidth(10)
    private String passed;
    @ExcelProperty("已考次数")
    @ColumnWidth(10)
    private Integer attempts;
    @ExcelProperty("最后交卷时间")
    @ColumnWidth(20)
    private String submitTime;
}
