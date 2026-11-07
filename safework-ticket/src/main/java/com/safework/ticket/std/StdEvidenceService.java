package com.safework.ticket.std;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.accident.AccidentService;
import com.safework.safety.check.CheckRules;
import com.safework.safety.chem.ChemService;
import com.safework.safety.chem.ChemVOs;
import com.safework.safety.contractor.ContractorRules;
import com.safework.safety.cost.CostRules;
import com.safework.safety.cost.CostService;
import com.safework.safety.cost.CostVOs;
import com.safework.safety.emergency.EmergencyRules;
import com.safework.safety.entity.CheckTask;
import com.safework.safety.entity.Contractor;
import com.safework.safety.entity.ContractorWorker;
import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.entity.EmergencyPlan;
import com.safework.safety.entity.Equipment;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.PersonCert;
import com.safework.safety.entity.PpeIssue;
import com.safework.safety.entity.PpeItem;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.equipment.EquipmentRules;
import com.safework.safety.health.HealthRules;
import com.safework.safety.health.HealthService;
import com.safework.safety.health.HealthVOs;
import com.safework.safety.mapper.CheckTaskMapper;
import com.safework.safety.mapper.ContractorMapper;
import com.safework.safety.mapper.ContractorWorkerMapper;
import com.safework.safety.mapper.EmergencyEventMapper;
import com.safework.safety.mapper.EmergencyPlanMapper;
import com.safework.safety.mapper.EquipmentMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.PersonCertMapper;
import com.safework.safety.mapper.PpeIssueMapper;
import com.safework.safety.mapper.PpeItemMapper;
import com.safework.safety.mapper.RiskPointMapper;
import com.safework.safety.moc.MocService;
import com.safework.safety.point.PointService;
import com.safework.safety.point.PointVOs;
import com.safework.safety.ppe.PpeRules;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.StdAssessment;
import com.safework.ticket.entity.StdItem;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.kpi.KpiService;
import com.safework.ticket.kpi.KpiVOs;
import com.safework.ticket.mapper.StdAssessmentMapper;
import com.safework.ticket.mapper.StdItemMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import com.safework.training.entity.TrainExamRecord;
import com.safework.training.enums.RecordStatus;
import com.safework.training.mapper.TrainExamRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 标准化自评的自动取证：从各模块取数，给每个考评项目列出证据（事实）和建议扣分。
 * <p>
 * 建议扣分只是参考，评审人可以改；手工项目（制度、文化等）系统没有数据，不给建议。
 * 只在页面上新建自评、重新取数时调用（会用到当前登录人的数据权限）。
 */
@Service
@RequiredArgsConstructor
public class StdEvidenceService {

    /** 一项的证据和建议扣分 */
    public record Evidence(List<String> facts, double suggested) {
    }

    private final KpiService kpiService;
    private final CostService costService;
    private final ChemService chemService;
    private final MocService mocService;
    private final AccidentService accidentService;
    private final HealthService healthService;
    private final UserService userService;
    private final PointService pointService;
    private final HazardMapper hazardMapper;
    private final CheckTaskMapper checkTaskMapper;
    private final EquipmentMapper equipmentMapper;
    private final PersonCertMapper certMapper;
    private final RiskPointMapper riskPointMapper;
    private final PpeIssueMapper ppeIssueMapper;
    private final PpeItemMapper ppeItemMapper;
    private final ContractorMapper contractorMapper;
    private final ContractorWorkerMapper workerMapper;
    private final EmergencyPlanMapper planMapper;
    private final EmergencyEventMapper eventMapper;
    private final WorkTicketMapper ticketMapper;
    private final TrainExamRecordMapper examRecordMapper;
    private final StdAssessmentMapper assessmentMapper;
    private final StdItemMapper itemMapper;

    /**
     * 取证
     *
     * @param excludeId 正在评的这次自评（找“上一次自评”时排除它）
     */
    public Map<String, Evidence> collect(Long excludeId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yearStart = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
        LocalDate soon = today.plusDays(30);
        Map<String, Evidence> m = new HashMap<>();
        KpiVOs.Report kpi = kpiService.report(today.getYear());

        // 1.1 安全生产目标
        {
            List<String> f = new ArrayList<>();
            double d = 0;
            if (kpi.defaultTargets()) {
                f.add(today.getYear() + " 年还没有设置年度安全目标（安全目标页面用的是系统默认目标）");
                d += 2;
            } else {
                f.add(today.getYear() + " 年已设置 " + kpi.items().size() + " 个安全目标指标，系统每月自动统计并发月报");
            }
            f.add("年初至今综合得分 " + fmt(kpi.totalScore()) + "：达标 " + kpi.green() + " 项、接近目标 " + kpi.yellow() + " 项、未达标 " + kpi.red() + " 项");
            if (kpi.totalScore() != null && kpi.totalScore() < 75) {
                d += 1;
            }
            m.put("1.1", new Evidence(f, d));
        }
        // 1.2 机构和职责
        {
            int safety = userService.listByRoleCode("safety").size();
            m.put("1.2", new Evidence(List.of("系统中安全员（安全管理人员）" + safety + " 人"), safety == 0 ? 2 : 0));
        }
        // 1.3 全员参与
        {
            KpiVOs.Item sign = kpiItem(kpi, "RESP_SIGN");
            if (sign == null || sign.denominator() == null || sign.denominator() == 0) {
                m.put("1.3", new Evidence(List.of(today.getYear() + " 年还没有发布安全生产责任书"), 4));
            } else {
                long unsigned = sign.denominator() - sign.numerator();
                m.put("1.3", new Evidence(List.of(today.getYear() + " 年度安全生产责任书已签署 " + sign.numerator() + " / " + sign.denominator() + " 人"
                        + (unsigned > 0 ? "，还有 " + unsigned + " 人未签署" : "")), StdRules.perCount(unsigned, 0.5, 3)));
            }
        }
        // 1.4 安全生产投入
        {
            CostVOs.Report cost = costService.report(today.getYear());
            List<String> f = new ArrayList<>();
            double d = 0;
            if (!cost.hasPlan()) {
                f.add(today.getYear() + " 年还没有编制安全生产费用计划");
                d += 2;
            } else {
                f.add("本年应提 " + CostRules.yuan(cost.plan().getAccrueAmount()) + " 元，当年可用 " + CostRules.yuan(cost.available()) + " 元");
                f.add("已使用 " + CostRules.yuan(cost.used()) + " 元（" + fmt(cost.usageRate()) + "%），支出台账 " + cost.expenseCount() + " 笔");
                if (cost.balance().signum() < 0) {
                    f.add("已用超过可用额度 " + CostRules.yuan(cost.balance().negate()) + " 元");
                    d += 1;
                }
                long over = cost.categories().stream().filter(c -> CostRules.OVER.equals(c.state()) || CostRules.UNPLANNED.equals(c.state())).count();
                if (over > 0) {
                    f.add(over + " 个使用类别超预算或无预算支出");
                    d += StdRules.perCount(over, 0.5, 1);
                }
            }
            m.put("1.4", new Evidence(f, d));
        }
        // 1.5 安全文化建设（隐患随手拍积分）
        {
            PointVOs.Overview p = pointService.overview(today.getYear());
            List<String> f = new ArrayList<>();
            f.add("隐患随手拍：" + today.getYear() + " 年 " + p.reporters() + " 人上报的 " + p.yearHazards() + " 条隐患被确认，共发出积分 " + p.yearPoints() + " 分");
            f.add("积分兑换奖品已发放 " + p.issued() + " 次（金额 " + CostRules.yuan(p.issuedAmount()) + " 元），待发放 " + p.pending() + " 次");
            m.put("1.5", new Evidence(f, p.yearHazards() == 0 ? 1 : 0));
        }
        // 3.1 教育培训管理
        {
            List<TrainExamRecord> recs = examRecordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                    .eq(TrainExamRecord::getStatus, RecordStatus.SUBMITTED).ge(TrainExamRecord::getSubmitTime, yearStart)
                    .select(TrainExamRecord::getExamId, TrainExamRecord::getPassed));
            long exams = recs.stream().map(TrainExamRecord::getExamId).distinct().count();
            long passed = recs.stream().filter(r -> Integer.valueOf(1).equals(r.getPassed())).count();
            List<String> f = new ArrayList<>();
            double d = 0;
            if (recs.isEmpty()) {
                f.add(today.getYear() + " 年还没有组织过考试");
                d = 5;
            } else {
                double rate = Math.round(passed * 1000.0 / recs.size()) / 10.0;
                f.add(today.getYear() + " 年组织考试 " + exams + " 场，交卷 " + recs.size() + " 份，合格 " + passed + " 份（" + rate + "%）");
                d += exams < 2 ? 1 : 0;
                d += rate < 90 ? 1 : 0;
            }
            m.put("3.1", new Evidence(f, d));
        }
        // 3.2 人员资质
        {
            long expired = certMapper.selectCount(Wrappers.<PersonCert>lambdaQuery().lt(PersonCert::getValidUntil, today));
            long expiring = certMapper.selectCount(Wrappers.<PersonCert>lambdaQuery().between(PersonCert::getValidUntil, today, soon));
            long total = certMapper.selectCount(Wrappers.<PersonCert>lambdaQuery());
            m.put("3.2", new Evidence(List.of("人员证书 " + total + " 本，已过期 " + expired + " 本，30 天内到期 " + expiring + " 本"),
                    StdRules.perCount(expired, 1, 5)));
        }
        // 4.1 设备设施管理 / 6.2 应急设施和物资
        {
            List<Equipment> eq = equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery().eq(Equipment::getStatus, EquipmentRules.IN_USE)
                    .select(Equipment::getEquipCode, Equipment::getName, Equipment::getCategory, Equipment::getStatus, Equipment::getNextInspectDate));
            List<Equipment> bad = eq.stream().filter(e -> {
                String st = EquipmentRules.state(e.getStatus(), e.getNextInspectDate(), today);
                return EquipmentRules.OVERDUE.equals(st) || EquipmentRules.NEVER.equals(st);
            }).toList();
            List<String> f = new ArrayList<>();
            f.add("在用设备 " + eq.size() + " 台，检验逾期或从未检验 " + bad.size() + " 台");
            bad.stream().limit(5).forEach(e -> f.add("待检：" + e.getEquipCode() + " " + e.getName()));
            m.put("4.1", new Evidence(f, StdRules.perCount(bad.size(), 1, 6)));
            long fire = eq.stream().filter(e -> "FIRE".equals(e.getCategory())).count();
            List<Equipment> badFire = bad.stream().filter(e -> "FIRE".equals(e.getCategory())).toList();
            List<String> f2 = new ArrayList<>();
            f2.add("在用消防和应急设施 " + fire + " 台（套），逾期未检 " + badFire.size() + " 台");
            badFire.stream().limit(3).forEach(e -> f2.add("待检：" + e.getEquipCode() + " " + e.getName()));
            m.put("6.2", new Evidence(f2, fire == 0 ? 2 : StdRules.perCount(badFire.size(), 1, 2)));
        }
        // 4.2 作业安全
        {
            long tickets = ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery().ge(WorkTicket::getCreateTime, yearStart));
            long overtime = ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery().eq(WorkTicket::getStatus, TicketStatus.IN_PROGRESS)
                    .lt(WorkTicket::getPlanEndTime, now));
            m.put("4.2", new Evidence(List.of(today.getYear() + " 年办理作业票 " + tickets + " 张，现在超过计划时间仍未结束的 " + overtime + " 张"),
                    tickets == 0 ? 6 : StdRules.perCount(overtime, 1, 6)));
        }
        // 4.3 危险化学品 / 5.2 重大危险源
        {
            ChemVOs.Overview chem = chemService.overview();
            List<String> f = new ArrayList<>();
            f.add("危化品目录 " + chem.chemicals() + " 种，存储单元 " + chem.stores() + " 个");
            f.add("缺 MSDS " + chem.noMsds() + " 种，超过设计最大储量 " + chem.overstock() + " 项");
            double d = StdRules.perCount(chem.noMsds(), 0.5, 2) + (chem.overstock() > 0 ? 2 : 0);
            m.put("4.3", new Evidence(f, d));
            List<String> f2 = new ArrayList<>();
            if (chem.stores() == 0) {
                f2.add("还没有录入危化品存储单元，没有开展重大危险源辨识（没有危化品的企业可以设为空项）");
            } else {
                f2.add("已按 GB 18218 对 " + chem.stores() + " 个存储单元辨识，构成重大危险源 " + chem.major() + " 处"
                        + (chem.major() > 0 ? "（一级 " + chem.level1() + "、二级 " + chem.level2() + "、三级 " + chem.level3() + "、四级 " + chem.level4() + "）" : ""));
            }
            m.put("5.2", new Evidence(f2, chem.stores() == 0 ? 3 : 0));
        }
        // 4.4 相关方管理
        {
            long blacklisted = contractorMapper.selectCount(Wrappers.<Contractor>lambdaQuery().eq(Contractor::getStatus, ContractorRules.BLACKLISTED));
            long expiring = contractorMapper.selectCount(Wrappers.<Contractor>lambdaQuery().eq(Contractor::getStatus, ContractorRules.APPROVED)
                    .and(w -> w.le(Contractor::getLicenseExpire, soon).or().le(Contractor::getInsuranceExpire, soon)));
            long untrained = workerMapper.selectCount(Wrappers.<ContractorWorker>lambdaQuery().eq(ContractorWorker::getStatus, 1)
                    .and(w -> w.isNull(ContractorWorker::getTrainingDate).or()
                            .lt(ContractorWorker::getTrainingDate, today.minusMonths(ContractorRules.TRAINING_MONTHS))));
            long total = contractorMapper.selectCount(Wrappers.<Contractor>lambdaQuery());
            m.put("4.4", new Evidence(List.of("承包商 " + total + " 家（黑名单 " + blacklisted + " 家），资质或保险快到期 / 已过期 " + expiring + " 家，入厂培训过期的人员 " + untrained + " 人"),
                    (expiring > 0 ? 1 : 0) + (untrained > 0 ? 1 : 0)));
        }
        // 4.5 职业健康
        {
            List<HealthVOs.LedgerRow> rows = healthService.compute(null, null, today);
            long contra = rows.stream().filter(HealthVOs.LedgerRow::contraindication).count();
            long overdue = rows.stream().filter(r -> HealthRules.OVERDUE.equals(r.state()) || HealthRules.NEVER.equals(r.state())).count();
            m.put("4.5", new Evidence(List.of("接害人员 " + rows.size() + " 人，体检逾期或未体检 " + overdue + " 人，职业禁忌证仍在接害岗位 " + contra + " 人"),
                    (contra > 0 ? 2 : 0) + (overdue > 0 ? 1 : 0)));
        }
        // 4.6 劳动防护用品
        {
            long expired = ppeIssueMapper.selectCount(Wrappers.<PpeIssue>lambdaQuery().eq(PpeIssue::getStatus, PpeRules.IN_USE).lt(PpeIssue::getExpireDate, today));
            long low = ppeItemMapper.selectCount(Wrappers.<PpeItem>lambdaQuery().eq(PpeItem::getStatus, 1).apply("stock < min_stock"));
            m.put("4.6", new Evidence(List.of("过期未更换的劳保用品 " + expired + " 件，库存不足的用品 " + low + " 种"),
                    (expired > 0 ? 1 : 0) + (low > 0 ? 1 : 0)));
        }
        // 5.1 安全风险管理
        {
            long points = riskPointMapper.selectCount(Wrappers.<RiskPoint>lambdaQuery().eq(RiskPoint::getStatus, 1));
            long overdue = riskPointMapper.selectCount(Wrappers.<RiskPoint>lambdaQuery().eq(RiskPoint::getStatus, 1).lt(RiskPoint::getNextCheckTime, now));
            m.put("5.1", new Evidence(List.of("已辨识风险点 " + points + " 个，巡检逾期 " + overdue + " 个"),
                    points == 0 ? 6 : StdRules.perCount(overdue, 1, 4)));
        }
        // 5.3 隐患排查治理
        {
            long overdue = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().eq(Hazard::getStatus, HazardStatus.RECTIFYING).lt(Hazard::getDeadline, now));
            long open = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                    .in(Hazard::getStatus, HazardStatus.REPORTED, HazardStatus.RECTIFYING, HazardStatus.PENDING_VERIFY));
            long checkOverdue = checkTaskMapper.selectCount(Wrappers.<CheckTask>lambdaQuery().eq(CheckTask::getStatus, CheckRules.PENDING).lt(CheckTask::getDueDate, today));
            KpiVOs.Item ontime = kpiItem(kpi, "HAZARD_ONTIME");
            KpiVOs.Item close = kpiItem(kpi, "HAZARD_CLOSE");
            List<String> f = new ArrayList<>();
            f.add("未闭环隐患 " + open + " 条，其中逾期未整改 " + overdue + " 条");
            if (ontime != null && ontime.actual() != null) {
                f.add("今年隐患按期整改率 " + fmt(ontime.actual()) + "%（" + ontime.numerator() + "/" + ontime.denominator() + "）");
            }
            if (close != null && close.actual() != null) {
                f.add("今年隐患闭环率 " + fmt(close.actual()) + "%（" + close.numerator() + "/" + close.denominator() + "）");
            }
            f.add("安全检查逾期未完成 " + checkOverdue + " 张");
            double d = StdRules.perCount(overdue, 1, 4) + (ontime != null && ontime.actual() != null && ontime.actual() < 90 ? 1 : 0) + (checkOverdue > 0 ? 1 : 0);
            m.put("5.3", new Evidence(f, d));
        }
        // 5.4 变更管理
        {
            var late = mocService.temporaryOpen().stream().filter(c -> c.getRestoreDate() != null && today.isAfter(c.getRestoreDate())).toList();
            List<String> f = new ArrayList<>();
            f.add("临时变更超期未恢复 " + late.size() + " 项");
            late.stream().limit(3).forEach(c -> f.add("超期未恢复：" + c.getChangeNo() + " " + c.getTitle() + "（应于 " + c.getRestoreDate() + " 恢复）"));
            m.put("5.4", new Evidence(f, StdRules.perCount(late.size(), 2, 4)));
        }
        // 6.1 应急准备
        {
            List<EmergencyPlan> plans = planMapper.selectList(Wrappers.<EmergencyPlan>lambdaQuery().eq(EmergencyPlan::getStatus, 1));
            long late = plans.stream().map(p -> EmergencyRules.drillState(p.getPlanType(), p.getLastDrillTime(), today).state())
                    .filter(s -> EmergencyRules.DRILL_NEVER.equals(s) || EmergencyRules.DRILL_OVERDUE.equals(s)).count();
            m.put("6.1", new Evidence(List.of("有效应急预案 " + plans.size() + " 份，未按期演练 " + late + " 份"),
                    plans.isEmpty() ? 5 : StdRules.perCount(late, 1, 3)));
        }
        // 6.3 应急处置与评估 / 7.1 事故报告
        {
            List<EmergencyEvent> events = eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery().ge(EmergencyEvent::getOccurTime, yearStart));
            long noSummary = events.stream().filter(e -> EmergencyRules.CLOSED.equals(e.getStatus()) && !StringUtils.hasText(e.getSummary())).count();
            long responding = events.stream().filter(e -> EmergencyRules.RESPONDING.equals(e.getStatus())).count();
            m.put("6.3", new Evidence(List.of(today.getYear() + " 年应急事件 " + events.size() + " 起，响应中 " + responding + " 起，结束后没有总结评估 " + noSummary + " 起"),
                    StdRules.perCount(noSummary, 1, 3)));
            long late = events.stream().filter(e -> e.getCreateTime() != null && e.getOccurTime() != null
                    && Duration.between(e.getOccurTime(), e.getCreateTime()).toHours() >= 1).count();
            m.put("7.1", new Evidence(List.of(today.getYear() + " 年事故 / 事件快报 " + events.size() + " 起，发生后超过 1 小时才上报的 " + late + " 起"),
                    late > 0 ? 2 : 0));
        }
        // 7.2 事故调查和处理
        {
            long lateInv = accidentService.openInvestigations().stream().filter(v -> today.isAfter(v.getDeadline())).count();
            long openInv = accidentService.openInvestigations().size();
            long lateMeasure = accidentService.openMeasures().stream().filter(x -> today.isAfter(x.getDeadline())).count();
            m.put("7.2", new Evidence(List.of("正在调查的事故 " + openInv + " 起（超期未结案 " + lateInv + " 起），整改措施逾期 " + lateMeasure + " 项"),
                    StdRules.perCount(lateInv, 2, 4) + StdRules.perCount(lateMeasure, 1, 4)));
        }
        // 8.1 绩效评定 / 8.2 持续改进
        {
            StdAssessment last = assessmentMapper.selectOne(Wrappers.<StdAssessment>lambdaQuery().eq(StdAssessment::getStatus, StdRules.DONE)
                    .ne(excludeId != null, StdAssessment::getId, excludeId).orderByDesc(StdAssessment::getFinishDate).last("LIMIT 1"));
            if (last == null) {
                m.put("8.1", new Evidence(List.of("系统里没有已完成的标准化自评"), 2));
            } else {
                boolean within = !last.getFinishDate().isBefore(today.minusYears(1));
                m.put("8.1", new Evidence(List.of("上次自评：" + last.getTitle() + "，" + last.getFinishDate() + " 完成，得分 " + fmt(last.getScore()) + "（" + last.getGrade() + "）"
                        + (within ? "" : "，距今已超过 1 年")), within ? 0 : 2));
            }
            List<String> f = new ArrayList<>();
            double d = 0;
            if (last != null) {
                List<Long> hazardIds = itemMapper.selectList(Wrappers.<StdItem>lambdaQuery().eq(StdItem::getAssessmentId, last.getId())
                        .isNotNull(StdItem::getHazardId).select(StdItem::getHazardId)).stream().map(StdItem::getHazardId).filter(Objects::nonNull).toList();
                long notClosed = hazardIds.isEmpty() ? 0 : hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().in(Hazard::getId, hazardIds)
                        .ne(Hazard::getStatus, HazardStatus.CLOSED).ne(Hazard::getStatus, HazardStatus.REVOKED));
                f.add("上次自评扣分项转整改隐患 " + hazardIds.size() + " 条，未闭环 " + notClosed + " 条");
                d += notClosed > 0 ? 1 : 0;
            }
            f.add("今年安全目标未达标指标 " + kpi.red() + " 项");
            d += kpi.red() > 0 ? 1 : 0;
            m.put("8.2", new Evidence(f, d));
        }
        // 统一按标准分封顶、按 0.5 取整
        Map<String, Evidence> result = new HashMap<>();
        m.forEach((code, e) -> result.put(code, new Evidence(e.facts(), StdRules.clamp(e.suggested(), StdRules.BY_CODE.get(code).score()))));
        return result;
    }

    /** 近一年（含今天往前 12 个月）的死亡、重伤人数：定级时看安全绩效 */
    public int[] performance() {
        LocalDateTime from = LocalDateTime.now().minusYears(1);
        int dead = 0;
        int serious = 0;
        for (EmergencyEvent e : eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery().ge(EmergencyEvent::getOccurTime, from)
                .select(EmergencyEvent::getDead, EmergencyEvent::getSeriousInjured))) {
            dead += e.getDead() == null ? 0 : e.getDead();
            serious += e.getSeriousInjured() == null ? 0 : e.getSeriousInjured();
        }
        return new int[]{dead, serious};
    }

    private static KpiVOs.Item kpiItem(KpiVOs.Report r, String code) {
        return r.items().stream().filter(i -> i.code().equals(code)).findFirst().orElse(null);
    }

    private static String fmt(Double v) {
        if (v == null) {
            return "-";
        }
        return v == Math.floor(v) ? String.valueOf(v.longValue()) : String.valueOf(v);
    }
}
