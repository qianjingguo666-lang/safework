package com.safework.ticket.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/** 作业票台账导出 */
@Data
@HeadRowHeight(22)
@ColumnWidth(18)
public class TicketExcelRow {
    @ExcelProperty("作业票编号")
    private String ticketNo;
    @ExcelProperty("作业类型")
    @ColumnWidth(14)
    private String ticketType;
    @ExcelProperty("作业名称")
    @ColumnWidth(26)
    private String title;
    @ExcelProperty("作业地点")
    @ColumnWidth(22)
    private String location;
    @ExcelProperty("受限空间")
    @ColumnWidth(22)
    private String spaceName;
    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;
    @ExcelProperty("申请人")
    private String applicantName;
    @ExcelProperty("监护人")
    private String guardianName;
    @ExcelProperty("审批人")
    private String approverName;
    @ExcelProperty("计划开始")
    private String planStartTime;
    @ExcelProperty("计划结束")
    private String planEndTime;
    @ExcelProperty("实际开始")
    private String actualStartTime;
    @ExcelProperty("实际结束")
    private String actualEndTime;
    @ExcelProperty("氧含量(%VOL)")
    @ColumnWidth(14)
    private String gasOxygen;
    @ExcelProperty("可燃气体(%LEL)")
    @ColumnWidth(15)
    private String gasLel;
    @ExcelProperty("硫化氢(mg/m3)")
    @ColumnWidth(15)
    private String gasH2s;
    @ExcelProperty("安全措施")
    @ColumnWidth(40)
    private String safetyMeasures;
}
