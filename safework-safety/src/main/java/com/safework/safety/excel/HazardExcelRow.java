package com.safework.safety.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/** 隐患台账导出（时间字段先格式化成字符串，避免 Excel 里显示成数字） */
@Data
@HeadRowHeight(22)
@ColumnWidth(18)
public class HazardExcelRow {
    @ExcelProperty("隐患编号")
    private String hazardNo;
    @ExcelProperty("隐患")
    @ColumnWidth(30)
    private String title;
    @ExcelProperty("等级")
    @ColumnWidth(10)
    private String level;
    @ExcelProperty("类别")
    @ColumnWidth(12)
    private String category;
    @ExcelProperty("位置")
    private String location;
    @ExcelProperty("受限空间")
    @ColumnWidth(22)
    private String spaceName;
    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;
    @ExcelProperty("是否逾期")
    @ColumnWidth(10)
    private String overdue;
    @ExcelProperty("上报人")
    private String reporterName;
    @ExcelProperty("上报时间")
    private String reportTime;
    @ExcelProperty("整改责任人")
    private String rectifierName;
    @ExcelProperty("整改期限")
    private String deadline;
    @ExcelProperty("整改措施")
    @ColumnWidth(30)
    private String rectifyMeasure;
    @ExcelProperty("整改完成时间")
    private String rectifyTime;
    @ExcelProperty("验收人")
    private String verifierName;
    @ExcelProperty("验收时间")
    private String verifyTime;
}
