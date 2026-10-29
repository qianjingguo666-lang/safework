package com.safework.ticket.excel;

import com.safework.system.excel.ExcelHelper;
import com.safework.ticket.dto.TicketQueryDTO;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.service.TicketService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 作业票台账导出：复用列表查询，数据权限和列表完全一致 */
@Service
@RequiredArgsConstructor
public class TicketExcelService {

    private static final int MAX_EXPORT = 5000;

    private final TicketService ticketService;

    public void export(TicketQueryDTO q, HttpServletResponse response) {
        q.setCurrent(1);
        q.setSize(MAX_EXPORT);
        List<TicketExcelRow> rows = ticketService.page(q).getRecords().stream().map(TicketExcelService::toRow).toList();
        ExcelHelper.write(response, "作业票台账", TicketExcelRow.class, rows);
    }

    private static TicketExcelRow toRow(WorkTicket t) {
        TicketExcelRow r = new TicketExcelRow();
        r.setTicketNo(t.getTicketNo());
        r.setTicketType(t.getTicketType() == null ? null : t.getTicketType().getLabel());
        r.setTitle(t.getTitle());
        r.setLocation(t.getLocation());
        r.setSpaceName(t.getSpaceName());
        r.setStatus(t.getStatus() == null ? null : t.getStatus().getLabel());
        r.setApplicantName(t.getApplicantName());
        r.setGuardianName(t.getGuardianName());
        r.setApproverName(t.getApproverName());
        r.setPlanStartTime(fmt(t.getPlanStartTime()));
        r.setPlanEndTime(fmt(t.getPlanEndTime()));
        r.setActualStartTime(fmt(t.getActualStartTime()));
        r.setActualEndTime(fmt(t.getActualEndTime()));
        r.setGasOxygen(num(t.getGasOxygen()));
        r.setGasLel(num(t.getGasLel()));
        r.setGasH2s(num(t.getGasH2s()));
        r.setSafetyMeasures(t.getSafetyMeasures());
        return r;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(ExcelHelper.CELL_TIME);
    }

    private static String num(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }
}
