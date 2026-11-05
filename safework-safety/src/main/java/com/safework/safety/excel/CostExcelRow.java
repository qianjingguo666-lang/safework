package com.safework.safety.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.math.BigDecimal;

/** 安全费用支出台账 */
@Data
@HeadRowHeight(22)
@ColumnWidth(14)
public class CostExcelRow {
    @ExcelProperty("编号")
    @ColumnWidth(18)
    private String expenseNo;
    @ExcelProperty("支出日期")
    private String expenseDate;
    @ExcelProperty("使用类别")
    @ColumnWidth(20)
    private String category;
    @ExcelProperty("支出事项")
    @ColumnWidth(30)
    private String title;
    @ExcelProperty("金额（元）")
    private BigDecimal amount;
    @ExcelProperty("使用部门")
    private String deptName;
    @ExcelProperty("收款单位")
    @ColumnWidth(22)
    private String payee;
    @ExcelProperty("凭证号")
    @ColumnWidth(16)
    private String voucherNo;
    @ExcelProperty("关联隐患")
    @ColumnWidth(24)
    private String hazard;
    @ExcelProperty("登记人")
    private String recorderName;
    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;
    @ExcelProperty("备注")
    @ColumnWidth(24)
    private String remark;
}
