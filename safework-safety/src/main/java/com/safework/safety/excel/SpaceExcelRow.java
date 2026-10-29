package com.safework.safety.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/** 受限空间台账 Excel 的一行（导入、导出共用；导入时按列名匹配，“状态”列可以不填） */
@Data
@HeadRowHeight(22)
@ColumnWidth(16)
public class SpaceExcelRow {
    @ExcelProperty("空间编号")
    private String spaceCode;
    @ExcelProperty("空间名称")
    @ColumnWidth(26)
    private String spaceName;
    @ExcelProperty("空间类型")
    private String spaceType;
    @ExcelProperty("位置")
    @ColumnWidth(22)
    private String location;
    @ExcelProperty("所属部门")
    private String deptName;
    @ExcelProperty("责任人")
    @ColumnWidth(20)
    private String responsibleName;
    @ExcelProperty("介质")
    @ColumnWidth(24)
    private String medium;
    @ExcelProperty("风险等级")
    @ColumnWidth(12)
    private String riskLevel;
    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;
    @ExcelProperty("备注")
    @ColumnWidth(30)
    private String remark;
}
