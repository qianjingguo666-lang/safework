package com.safework.ticket.commit;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.safety.chem.ChemService;
import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.moc.MocService;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.RiskCommitment;
import com.safework.ticket.entity.RiskJudgment;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.enums.TicketType;
import com.safework.ticket.mapper.RiskCommitmentMapper;
import com.safework.ticket.mapper.RiskJudgmentMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import com.safework.ticket.sign.SignatureImage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 安全风险研判与承诺公告。
 * <pre>
 * 生产 / 设备 / 工艺 / 安全负责人逐项研判（异常的写情况和管控措施；生产负责人填装置运行情况）
 *      │  四个方面都研判了
 *      ▼
 * 主要负责人手写签名承诺 ──▶ 系统统计（特殊作业票、重大危险源、超储、未整改隐患、临时变更）固定下来 ──▶ 公告
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class CommitService {

    public static final String PERM_LIST = "commit:list";
    public static final String PERM_JUDGE = "commit:judge";
    public static final String PERM_SIGN = "commit:sign";
    public static final String BIZ_TYPE = "RISK_COMMIT";
    public static final String LINK = "/safety/commit";
    private static final List<HazardStatus> OPEN = List.of(HazardStatus.REPORTED, HazardStatus.RECTIFYING, HazardStatus.PENDING_VERIFY);
    private static final List<TicketStatus> ACTIVE = List.of(TicketStatus.APPROVED, TicketStatus.IN_PROGRESS, TicketStatus.COMPLETED);

    private final RiskCommitmentMapper commitmentMapper;
    private final RiskJudgmentMapper judgmentMapper;
    private final WorkTicketMapper ticketMapper;
    private final HazardMapper hazardMapper;
    private final ChemService chemService;
    private final MocService mocService;
    private final UserService userService;
    private final ObjectMapper json = new ObjectMapper();

    // ============================== 查询 ==============================

    public CommitVOs.Today today() {
        LocalDate date = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        RiskCommitment c = byDate(date);
        List<RiskJudgment> js = c == null ? List.of() : judgments(c.getId());
        List<String> missing = CommitRules.missingAspects(js.stream().map(RiskJudgment::getAspect).collect(Collectors.toSet()));
        boolean committed = c != null && CommitRules.COMMITTED.equals(c.getStatus());
        CommitVOs.Stats stats = committed ? statsOf(c) : liveStats(date);
        RiskCommitment last = commitmentMapper.selectOne(Wrappers.<RiskCommitment>lambdaQuery().isNotNull(RiskCommitment::getUnitsTotal)
                .lt(RiskCommitment::getCommitDate, date).orderByDesc(RiskCommitment::getCommitDate).last("LIMIT 1"));
        CommitRules.Units lastUnits = last == null ? null : new CommitRules.Units(last.getUnitsTotal(), last.getUnitsRunning(), last.getUnitsStopped(), last.getUnitsOverhaul());
        return new CommitVOs.Today(date, c, aspects(js), stats, missing,
                CommitRules.judgeLate(date, now, missing.isEmpty()), CommitRules.commitLate(date, now, committed),
                !committed && StpUtil.hasPermission(PERM_JUDGE),
                !committed && c != null && missing.isEmpty() && StpUtil.hasPermission(PERM_SIGN), lastUnits);
    }

    /** 公告：指定日期的；不指定就是最近一次的 */
    public CommitVOs.Board board(LocalDate date) {
        RiskCommitment c = commitmentMapper.selectOne(Wrappers.<RiskCommitment>lambdaQuery().eq(RiskCommitment::getStatus, CommitRules.COMMITTED)
                .eq(date != null, RiskCommitment::getCommitDate, date).orderByDesc(RiskCommitment::getCommitDate).last("LIMIT 1"));
        if (c == null) {
            throw new BizException(404, date == null ? "还没有承诺公告" : date + " 没有承诺公告");
        }
        return new CommitVOs.Board(c, statsOf(c), aspects(judgments(c.getId())), commitmentMapper.signImage(c.getId()));
    }

    /** 某月每天的情况和按时承诺率（今天 10:00 以前还没到期，不算进应承诺天数） */
    public CommitVOs.History history(YearMonth month) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Map<LocalDate, RiskCommitment> byDate = commitmentMapper.selectList(Wrappers.<RiskCommitment>lambdaQuery()
                        .ge(RiskCommitment::getCommitDate, month.atDay(1)).le(RiskCommitment::getCommitDate, month.atEndOfMonth()))
                .stream().collect(Collectors.toMap(RiskCommitment::getCommitDate, Function.identity(), (a, b) -> a));
        List<CommitVOs.HistoryRow> rows = new ArrayList<>();
        int days = 0;
        int onTime = 0;
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()) && !d.isAfter(today); d = d.plusDays(1)) {
            RiskCommitment c = byDate.get(d);
            boolean committed = c != null && CommitRules.COMMITTED.equals(c.getStatus());
            boolean ok = committed && CommitRules.onTime(d, c.getCommitTime());
            if (!d.equals(today) || CommitRules.commitLate(d, now, false) || ok) {
                days++;
                if (ok) {
                    onTime++;
                }
            }
            rows.add(new CommitVOs.HistoryRow(d, c == null ? CommitRules.NONE : c.getStatus(), c == null ? null : c.getId(),
                    c == null ? null : c.getCommitTime(), ok, c == null ? null : c.getAbnormalCount(), c == null ? null : c.getCommitterName()));
        }
        java.util.Collections.reverse(rows);
        return new CommitVOs.History(month.toString(), rows, days, onTime, CommitRules.rate(onTime, days));
    }

    /** 概况：不依赖登录 */
    public CommitVOs.Overview overview(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        RiskCommitment c = byDate(date);
        List<String> missing = c == null ? new ArrayList<>(CommitRules.ASPECTS.keySet())
                : CommitRules.missingAspects(judgments(c.getId()).stream().map(RiskJudgment::getAspect).collect(Collectors.toSet()));
        boolean committed = c != null && CommitRules.COMMITTED.equals(c.getStatus());
        CommitVOs.History h = history(YearMonth.from(date));
        return new CommitVOs.Overview(date, c == null ? CommitRules.NONE : c.getStatus(), missing, committed,
                CommitRules.judgeLate(date, now, missing.isEmpty()), CommitRules.commitLate(date, now, committed),
                c == null ? null : c.getAbnormalCount(), h.days(), h.onTimeDays(), h.rate());
    }

    // ============================== 研判 ==============================

    @Transactional(rollbackFor = Exception.class)
    public void judge(CommitDTOs.Judge dto) {
        List<CommitRules.Result> results = dto.getResults().stream()
                .map(r -> new CommitRules.Result(r.getCode(), Boolean.TRUE.equals(r.getOk()), trim(r.getNote()), trim(r.getMeasure()))).toList();
        String err = CommitRules.checkJudgment(dto.getAspect(), results);
        if (err != null) {
            throw new BizException(err);
        }
        boolean production = "PRODUCTION".equals(dto.getAspect());
        if (production) {
            err = CommitRules.checkUnits(new CommitRules.Units(dto.getUnitsTotal(), dto.getUnitsRunning(), dto.getUnitsStopped(), dto.getUnitsOverhaul()));
            if (err != null) {
                throw new BizException(err);
            }
        }
        RiskCommitment c = getOrCreate(LocalDate.now());
        if (CommitRules.COMMITTED.equals(c.getStatus())) {
            throw new BizException("今天已经承诺公告了，不能再修改研判");
        }
        SysUser me = me();
        RiskJudgment j = judgmentMapper.selectOne(Wrappers.<RiskJudgment>lambdaQuery().eq(RiskJudgment::getCommitmentId, c.getId())
                .eq(RiskJudgment::getAspect, dto.getAspect()));
        boolean isNew = j == null;
        if (isNew) {
            j = new RiskJudgment();
            j.setCommitmentId(c.getId());
            j.setAspect(dto.getAspect());
        }
        j.setJudgerId(me.getId());
        j.setJudgerName(me.getNickname());
        j.setJudgeTime(LocalDateTime.now());
        j.setAbnormalCount(CommitRules.abnormal(results));
        try {
            j.setItems(json.writeValueAsString(results));
        } catch (Exception e) {
            throw new BizException("研判结果保存失败");
        }
        if (isNew) {
            judgmentMapper.insert(j);
        } else {
            judgmentMapper.updateById(j);
        }
        if (production) {
            c.setUnitsTotal(dto.getUnitsTotal());
            c.setUnitsRunning(dto.getUnitsRunning());
            c.setUnitsStopped(dto.getUnitsStopped());
            c.setUnitsOverhaul(dto.getUnitsOverhaul());
            c.setTrialRun(Boolean.TRUE.equals(dto.getTrialRun()) ? 1 : 0);
            c.setStartupShutdown(Boolean.TRUE.equals(dto.getStartupShutdown()) ? 1 : 0);
            c.setConditionNote(trim(dto.getConditionNote()));
        }
        c.setAbnormalCount(judgments(c.getId()).stream().mapToInt(RiskJudgment::getAbnormalCount).sum());
        commitmentMapper.updateById(c);
    }

    // ============================== 承诺 ==============================

    @Transactional(rollbackFor = Exception.class)
    public void commit(CommitDTOs.Commit dto) {
        LocalDate date = LocalDate.now();
        RiskCommitment c = byDate(date);
        if (c == null) {
            throw new BizException("今天还没有人研判，不能承诺");
        }
        if (CommitRules.COMMITTED.equals(c.getStatus())) {
            throw new BizException("今天已经承诺公告了");
        }
        List<String> missing = CommitRules.missingAspects(judgments(c.getId()).stream().map(RiskJudgment::getAspect).collect(Collectors.toSet()));
        if (!missing.isEmpty()) {
            throw new BizException("还有 " + missing.stream().map(CommitRules.ASPECTS::get).collect(Collectors.joining("、")) + " 没有研判");
        }
        SignatureImage.Checked sign;
        try {
            sign = SignatureImage.check(dto.getSignImage());
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        CommitVOs.Stats s = liveStats(date);
        c.setHotWorkCount(s.hotWork());
        c.setConfinedCount(s.confined());
        c.setHeightCount(s.height());
        c.setMajorSources(s.majorSources());
        c.setOverstock(s.overstock());
        c.setOpenMajorHazards(s.openMajorHazards());
        c.setOpenHazards(s.openHazards());
        c.setMocTemporary(s.mocTemporary());
        SysUser me = me();
        c.setStatus(CommitRules.COMMITTED);
        c.setContent(CommitRules.commitmentText(c.getAbnormalCount() == null ? 0 : c.getAbnormalCount()));
        c.setCommitterId(me.getId());
        c.setCommitterName(me.getNickname());
        c.setCommitTime(LocalDateTime.now());
        c.setSignImage(sign.dataUrl());
        c.setSignHash(sign.sha256());
        commitmentMapper.updateById(c);
    }

    // ============================== 内部 ==============================

    /** 现在的系统统计（承诺时固定到记录里） */
    CommitVOs.Stats liveStats(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        Map<TicketType, Long> tickets = ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                        .select(WorkTicket::getId, WorkTicket::getTicketType)
                        .in(WorkTicket::getStatus, ACTIVE)
                        .lt(WorkTicket::getPlanStartTime, to).ge(WorkTicket::getPlanEndTime, from))
                .stream().collect(Collectors.groupingBy(WorkTicket::getTicketType, Collectors.counting()));
        int open = Math.toIntExact(hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().in(Hazard::getStatus, OPEN)));
        int openMajor = Math.toIntExact(hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().in(Hazard::getStatus, OPEN).eq(Hazard::getLevel, "MAJOR")));
        return new CommitVOs.Stats(tickets.getOrDefault(TicketType.HOT_WORK, 0L).intValue(), tickets.getOrDefault(TicketType.CONFINED_SPACE, 0L).intValue(),
                tickets.getOrDefault(TicketType.HEIGHT, 0L).intValue(), chemService.overview().major(), chemService.overstocked().size(),
                openMajor, open, mocService.temporaryOpen().size());
    }

    private static CommitVOs.Stats statsOf(RiskCommitment c) {
        return new CommitVOs.Stats(n(c.getHotWorkCount()), n(c.getConfinedCount()), n(c.getHeightCount()), n(c.getMajorSources()), n(c.getOverstock()),
                n(c.getOpenMajorHazards()), n(c.getOpenHazards()), n(c.getMocTemporary()));
    }

    private List<CommitVOs.AspectView> aspects(List<RiskJudgment> js) {
        Map<String, RiskJudgment> by = js.stream().collect(Collectors.toMap(RiskJudgment::getAspect, Function.identity(), (a, b) -> a));
        List<CommitVOs.AspectView> l = new ArrayList<>();
        CommitRules.ASPECTS.forEach((aspect, label) -> {
            RiskJudgment j = by.get(aspect);
            Map<String, CommitRules.Result> rs = j == null ? Map.of() : parse(j.getItems()).stream()
                    .collect(Collectors.toMap(CommitRules.Result::code, Function.identity(), (a, b) -> a));
            List<CommitVOs.ItemView> items = CommitRules.itemsOf(aspect).stream().map(i -> {
                CommitRules.Result r = rs.get(i.code());
                return new CommitVOs.ItemView(i.code(), i.text(), r == null ? null : r.ok(), r == null ? null : r.note(), r == null ? null : r.measure());
            }).toList();
            l.add(new CommitVOs.AspectView(aspect, label, j == null ? null : j.getJudgerName(), j == null ? null : j.getJudgeTime(),
                    j == null ? 0 : j.getAbnormalCount(), items));
        });
        return l;
    }

    private List<CommitRules.Result> parse(String items) {
        try {
            return json.readValue(items, new TypeReference<List<CommitRules.Result>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private RiskCommitment getOrCreate(LocalDate date) {
        RiskCommitment c = byDate(date);
        if (c != null) {
            return c;
        }
        c = new RiskCommitment();
        c.setCommitDate(date);
        c.setStatus(CommitRules.JUDGING);
        c.setAbnormalCount(0);
        try {
            commitmentMapper.insert(c);
            return c;
        } catch (DuplicateKeyException e) {
            // 两个负责人同时研判，另一个人已经创建了
            return byDate(date);
        }
    }

    private RiskCommitment byDate(LocalDate date) {
        return commitmentMapper.selectOne(Wrappers.<RiskCommitment>lambdaQuery().eq(RiskCommitment::getCommitDate, date));
    }

    private List<RiskJudgment> judgments(Long commitmentId) {
        return judgmentMapper.selectList(Wrappers.<RiskJudgment>lambdaQuery().eq(RiskJudgment::getCommitmentId, commitmentId));
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
