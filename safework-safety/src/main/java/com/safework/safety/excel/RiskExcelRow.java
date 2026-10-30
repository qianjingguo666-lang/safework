package com.safework.safety.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/** 风险分级管控清单 */
@Data
@HeadRowHeight(22)
@ColumnWidth(14)
public class RiskExcelRow {
    @ExcelProperty("编号")
    @ColumnWidth(10)
    private String pointCode;
    @ExcelProperty("风险点")
    @ColumnWidth(24)
    private String pointName;
    @ExcelProperty("类型")
    @ColumnWidth(10)
    private String pointType;
    @ExcelProperty("位置")
    private String location;
    @ExcelProperty("责任部门")
    private String deptName;
    @ExcelProperty("责任人")
    @ColumnWidth(18)
    private String responsibleName;
    @ExcelProperty("危险有害因素")
    @ColumnWidth(30)
    private String hazardFactors;
    @ExcelProperty("可能导致的事故")
    @ColumnWidth(18)
    private String possibleAccidents;
    @ExcelProperty("L")
    @ColumnWidth(6)
    private Double lecL;
    @ExcelProperty("E")
    @ColumnWidth(6)
    private Double lecE;
    @ExcelProperty("C")
    @ColumnWidth(6)
    private Double lecC;
    @ExcelProperty("D")
    @ColumnWidth(8)
    private Double lecD;
    @ExcelProperty("风险等级")
    @ColumnWidth(10)
    private String riskLevel;
    @ExcelProperty("管控层级")
    @ColumnWidth(10)
    private String controlLevel;
    @ExcelProperty("管控措施")
    @ColumnWidth(50)
    private String controlMeasures;
    @ExcelProperty("巡检周期（天）")
    @ColumnWidth(12)
    private Integer checkCycleDays;
    @ExcelProperty("上次巡检")
    @ColumnWidth(17)
    private String lastCheckTime;
    @ExcelProperty("下次巡检")
    @ColumnWidth(17)
    private String nextCheckTime;
}
