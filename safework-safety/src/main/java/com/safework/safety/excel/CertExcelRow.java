package com.safework.safety.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/** 人员资质台账 */
@Data
@HeadRowHeight(22)
@ColumnWidth(14)
public class CertExcelRow {
    @ExcelProperty("姓名")
    @ColumnWidth(18)
    private String userName;
    @ExcelProperty("部门")
    private String deptName;
    @ExcelProperty("证书类型")
    @ColumnWidth(30)
    private String certType;
    @ExcelProperty("证书编号")
    @ColumnWidth(22)
    private String certNo;
    @ExcelProperty("发证机关")
    @ColumnWidth(20)
    private String issuer;
    @ExcelProperty("发证日期")
    private String issueDate;
    @ExcelProperty("有效期至")
    private String expireDate;
    @ExcelProperty("下次复审")
    private String reviewDate;
    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String state;
    @ExcelProperty("备注")
    @ColumnWidth(24)
    private String remark;
}
