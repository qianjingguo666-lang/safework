package com.safework.system.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.safework.common.exception.BizException;
import com.safework.system.file.FileTypeDetector;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/** Excel 导入导出（EasyExcel）。表头、列宽写在行对象的注解上 */
@Slf4j
public final class ExcelHelper {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    public static final DateTimeFormatter CELL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ExcelHelper() {
    }

    /** 导出：文件名自动加上时间，如 受限空间台账_202610291030.xlsx */
    public static <T> void write(HttpServletResponse response, String name, Class<T> head, Collection<T> rows) {
        String fileName = URLEncoder.encode(name + "_" + LocalDateTime.now().format(TIME) + ".xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType(XLSX);
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        try {
            EasyExcel.write(response.getOutputStream(), head).sheet(name).doWrite(rows);
        } catch (IOException e) {
            log.warn("导出 Excel 中断：{}", e.getMessage());
        }
    }

    /** 导入模板：第一个工作表只有表头（用户在这里填数据），第二个工作表是填写说明 */
    public static <T> void writeTemplate(HttpServletResponse response, String name, Class<T> head, List<String> notes) {
        String fileName = URLEncoder.encode(name + "导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(XLSX);
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        try {
            ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
            writer.write(List.of(), EasyExcel.writerSheet(0, name).head(head).build());
            writer.write(notes.stream().map(NoteRow::new).toList(), EasyExcel.writerSheet(1, "填写说明").head(NoteRow.class).build());
            writer.finish();
        } catch (IOException e) {
            log.warn("导出模板中断：{}", e.getMessage());
        }
    }

    /** 导入：读取第一个工作表，表头按列名匹配（列的顺序可以调整） */
    public static <T> List<T> read(MultipartFile file, Class<T> head, int maxRows) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择 Excel 文件");
        }
        String ext = FileTypeDetector.extOf(file.getOriginalFilename());
        if (!"xlsx".equals(ext) && !"xls".equals(ext)) {
            throw new BizException("只支持 .xlsx / .xls 文件，请使用下载的模板");
        }
        List<T> rows;
        try (InputStream in = file.getInputStream()) {
            rows = EasyExcel.read(in).head(head).sheet().doReadSync();
        } catch (Exception e) {
            log.warn("解析 Excel 失败：{}", e.getMessage());
            throw new BizException("Excel 解析失败，请确认文件没有损坏，并且使用的是下载的模板");
        }
        if (rows.size() > maxRows) {
            throw new BizException("一次最多导入 " + maxRows + " 行，请分批导入");
        }
        return rows;
    }
}
