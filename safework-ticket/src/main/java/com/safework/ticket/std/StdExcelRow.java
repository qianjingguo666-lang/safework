package com.safework.ticket.std;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/** 标准化自评扣分汇总 */
@Data
@HeadRowHeight(22)
@ColumnWidth(12)
public class StdExcelRow {
    @ExcelProperty("编号")
    @ColumnWidth(8)
    private String code;
    @ExcelProperty("一级要素")
    @ColumnWidth(24)
    private String element;
    @ExcelProperty("考评项目")
    @ColumnWidth(20)
    private String title;
    @ExcelProperty("考评内容")
    @ColumnWidth(50)
    private String content;
    @ExcelProperty("标准分")
    private Integer standardScore;
    @ExcelProperty("空项")
    @ColumnWidth(8)
    private String na;
    @ExcelProperty("扣分")
    private Double deduct;
    @ExcelProperty("得分")
    private Double got;
    @ExcelProperty("扣分说明")
    @ColumnWidth(40)
    private String note;
    @ExcelProperty("整改隐患")
    @ColumnWidth(20)
    private String hazardNo;
}
