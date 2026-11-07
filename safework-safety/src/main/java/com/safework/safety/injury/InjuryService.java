package com.safework.safety.injury;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.entity.InjuryCase;
import com.safework.safety.entity.InjuryCost;
import com.safework.safety.mapper.EmergencyEventMapper;
import com.safework.safety.mapper.InjuryCaseMapper;
import com.safework.safety.mapper.InjuryCostMapper;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.system.dto.UserOptionVO;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工伤管理：登记（可以从事故快报带出）→ 30 日内申请工伤认定（倒计时提醒）→ 认定结果 → 劳动能力鉴定（伤残等级）→ 结案；
 * 停工留薪期（一般 ≤ 12 个月）和费用台账（基金 / 单位）贯穿全程。
 * deadlines / overview 不用登录信息，定时任务、大屏、智能体可以调用。
 */
@Service
@RequiredArgsConstructor
public class InjuryService {

    public static final String PERM_LIST = "injury:list";
    public static final String PERM_EDIT = "injury:edit";
    public static final String LINK = "/safety/injury";
    public static final String BIZ_TYPE = "INJURY";

    private final InjuryCaseMapper caseMapper;
    private final InjuryCostMapper costMapper;
    private final EmergencyEventMapper eventMapper;
    private final SerialNoGenerator serialNo;
    private final UserService userService;

    // ============================== 查询 ==============================

    public InjuryVOs.Page page(InjuryDTOs.Query q) {
        LocalDate today = LocalDate.now();
        String kw = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null;
        Page<InjuryCase> page = caseMapper.selectPage(Page.of(q.getCurrent(), Math.min(q.getSize(), 100)), Wrappers.<InjuryCase>lambdaQuery()
                .eq(StringUtils.hasText(q.getStatus()), InjuryCase::getStatus, q.getStatus())
                .ge(q.getYear() != null, InjuryCase::getInjuryTime, q.getYear() == null ? null : LocalDate.of(q.getYear(), 1, 1).atStartOfDay())
                .lt(q.getYear() != null, InjuryCase::getInjuryTime, q.getYear() == null ? null : LocalDate.of(q.getYear() + 1, 1, 1).atStartOfDay())
                .and(kw != null, w -> w.like(InjuryCase::getInjuredName, kw).or().like(InjuryCase::getCaseNo, kw).or().like(InjuryCase::getEmployerName, kw))
                .orderByAsc(InjuryCase::getStatus).orderByDesc(InjuryCase::getInjuryTime));
        Map<Long, BigDecimal> totals = totals(page.getRecords().stream().map(InjuryCase::getId).toList());
        return new InjuryVOs.Page(page.getRecords().stream().map(c -> new InjuryVOs.Row(c, deadline(c, today),
                totals.getOrDefault(c.getId(), BigDecimal.ZERO))).toList(), page.getTotal());
    }

    public InjuryVOs.Detail detail(Long id) {
        InjuryCase c = load(id);
        List<InjuryCost> costs = costMapper.selectList(Wrappers.<InjuryCost>lambdaQuery().eq(InjuryCost::getCaseId, id)
                .orderByAsc(InjuryCost::getPayDate).orderByAsc(InjuryCost::getCreateTime));
        BigDecimal fund = sum(costs.stream().filter(x -> "FUND".equals(x.getPayer())).toList());
        BigDecimal company = sum(costs.stream().filter(x -> "COMPANY".equals(x.getPayer())).toList());
        List<String> actions = new ArrayList<>();
        if (StpUtil.hasPermission(PERM_EDIT)) {
            for (InjuryRules.Action a : InjuryRules.Action.values()) {
                if (a.canFire(c.getStatus())) {
                    actions.add(a.name());
                }
            }
        }
        Integer months = c.getGrade() == null ? null : InjuryRules.lumpSumMonths(c.getGrade());
        return new InjuryVOs.Detail(c, deadline(c, LocalDate.now()), costs, fund, company, InjuryRules.gradeLabel(c.getGrade()), months,
                c.getGrade() == null ? null : InjuryRules.lumpSum(c.getGrade(), c.getMonthlyWage()), actions);
    }

    /** 还没申请认定的（定时任务、大屏用） */
    public List<InjuryVOs.Row> pendingApply(LocalDate today) {
        return caseMapper.selectList(Wrappers.<InjuryCase>lambdaQuery().eq(InjuryCase::getStatus, InjuryRules.REPORTED)
                        .orderByAsc(InjuryCase::getInjuryTime))
                .stream().map(c -> new InjuryVOs.Row(c, deadline(c, today), BigDecimal.ZERO)).toList();
    }

    public InjuryVOs.Overview overview() {
        LocalDate today = LocalDate.now();
        List<InjuryCase> year = caseMapper.selectList(Wrappers.<InjuryCase>lambdaQuery().ge(InjuryCase::getInjuryTime, LocalDate.of(today.getYear(), 1, 1).atStartOfDay()));
        List<InjuryVOs.Row> pending = pendingApply(today);
        long open = caseMapper.selectCount(Wrappers.<InjuryCase>lambdaQuery().ne(InjuryCase::getStatus, InjuryRules.CLOSED));
        List<InjuryCost> costs = costMapper.selectList(Wrappers.<InjuryCost>lambdaQuery().ge(InjuryCost::getPayDate, LocalDate.of(today.getYear(), 1, 1)));
        return new InjuryVOs.Overview(year.size(), pending.size(), (int) pending.stream().filter(r -> InjuryRules.OVERDUE.equals(r.deadline().state())).count(),
                (int) open, sum(costs.stream().filter(x -> "FUND".equals(x.getPayer())).toList()), sum(costs.stream().filter(x -> "COMPANY".equals(x.getPayer())).toList()));
    }

    // ============================== 登记和流转 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long id, InjuryDTOs.Save d) {
        InjuryCase c = id == null ? new InjuryCase() : load(id);
        if (id != null && !InjuryRules.REPORTED.equals(c.getStatus())) {
            throw new BizException("已经申请认定了，不能再修改基本信息");
        }
        if (d.getInjuryTime().isAfter(java.time.LocalDateTime.now())) {
            throw new BizException("受伤时间不能晚于现在");
        }
        if (InjuryRules.OWN.equals(d.getEmployerType())) {
            if (d.getInjuredUserId() == null) {
                throw new BizException("请选择受伤的员工");
            }
            SysUser u = userService.getById(d.getInjuredUserId());
            Map<Long, UserOptionVO> opts = userService.options().stream().collect(Collectors.toMap(UserOptionVO::getId, o -> o, (a, b) -> a));
            c.setInjuredUserId(u.getId());
            c.setInjuredName(u.getNickname());
            c.setEmployerName("本企业");
            c.setDeptName(opts.containsKey(u.getId()) ? opts.get(u.getId()).getDeptName() : null);
        } else if (InjuryRules.CONTRACTOR.equals(d.getEmployerType())) {
            if (!StringUtils.hasText(d.getInjuredName()) || !StringUtils.hasText(d.getEmployerName())) {
                throw new BizException("承包商人员要填写姓名和所在单位（由该单位申请工伤认定）");
            }
            c.setInjuredUserId(null);
            c.setInjuredName(d.getInjuredName().trim());
            c.setEmployerName(d.getEmployerName().trim());
            c.setDeptName(null);
        } else {
            throw new BizException("请选择本企业员工还是承包商人员");
        }
        c.setEmployerType(d.getEmployerType());
        c.setInjuryTime(d.getInjuryTime());
        c.setLocation(d.getLocation().trim());
        c.setDescription(d.getDescription().trim());
        c.setInjuryPart(trim(d.getInjuryPart()));
        c.setInjuryNature(trim(d.getInjuryNature()));
        if (d.getEventId() != null) {
            EmergencyEvent e = eventMapper.selectById(d.getEventId());
            if (e == null) {
                throw new BizException("关联的事故快报不存在");
            }
            c.setEventId(e.getId());
            c.setEventNo(e.getEventNo());
        } else {
            c.setEventId(null);
            c.setEventNo(null);
        }
        if (id == null) {
            c.setCaseNo(serialNo.next("GS"));
            c.setStatus(InjuryRules.REPORTED);
            c.setRecorderName(me().getNickname());
            caseMapper.insert(c);
        } else {
            caseMapper.updateById(c);
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void apply(Long id, InjuryDTOs.Apply d) {
        InjuryCase c = load(id);
        if (d.getApplyDate().isBefore(c.getInjuryTime().toLocalDate()) || d.getApplyDate().isAfter(LocalDate.now())) {
            throw new BizException("申请日期要在受伤日期和今天之间");
        }
        c.setApplyDate(d.getApplyDate());
        c.setApplyNo(trim(d.getApplyNo()));
        fire(c, InjuryRules.Action.APPLY);
    }

    @Transactional(rollbackFor = Exception.class)
    public void decide(Long id, InjuryDTOs.Decide d) {
        InjuryCase c = load(id);
        if (c.getApplyDate() != null && d.getDecisionDate().isBefore(c.getApplyDate())) {
            throw new BizException("认定日期不能早于申请日期");
        }
        c.setDecisionDate(d.getDecisionDate());
        c.setDecisionNo(trim(d.getDecisionNo()));
        c.setDecisionNote(trim(d.getNote()));
        fire(c, Boolean.TRUE.equals(d.getRecognized()) ? InjuryRules.Action.RECOGNIZE : InjuryRules.Action.NOT_RECOGNIZE);
    }

    @Transactional(rollbackFor = Exception.class)
    public void appraise(Long id, InjuryDTOs.Appraise d) {
        InjuryCase c = load(id);
        c.setGrade(d.getGrade());
        c.setAppraiseDate(d.getAppraiseDate());
        c.setMonthlyWage(d.getMonthlyWage());
        fire(c, InjuryRules.Action.APPRAISE);
    }

    @Transactional(rollbackFor = Exception.class)
    public void leave(Long id, InjuryDTOs.Leave d) {
        InjuryCase c = load(id);
        if (InjuryRules.CLOSED.equals(c.getStatus()) || InjuryRules.NOT_RECOGNIZED.equals(c.getStatus())) {
            throw new BizException("已结案或不予认定的不能再修改停工留薪期");
        }
        boolean ext = Boolean.TRUE.equals(d.getExtended());
        String err = InjuryRules.checkLeave(d.getLeaveStart(), d.getLeaveEnd(), ext, c.getInjuryTime().toLocalDate());
        if (err != null) {
            throw new BizException(err);
        }
        if (d.getReturnDate() != null && d.getReturnDate().isBefore(d.getLeaveStart())) {
            throw new BizException("复工日期不能早于停工开始日期");
        }
        c.setLeaveStart(d.getLeaveStart());
        c.setLeaveEnd(d.getLeaveEnd());
        c.setLeaveExtended(ext ? 1 : 0);
        c.setReturnDate(d.getReturnDate());
        caseMapper.updateById(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String note) {
        InjuryCase c = load(id);
        c.setCloseDate(LocalDate.now());
        c.setCloseNote(trim(note));
        fire(c, InjuryRules.Action.CLOSE);
    }

    // ============================== 费用 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long addCost(Long caseId, InjuryDTOs.Cost d) {
        InjuryCase c = load(caseId);
        if (!InjuryRules.COST_CATEGORIES.containsKey(d.getCategory()) || !InjuryRules.PAYERS.containsKey(d.getPayer())) {
            throw new BizException("费用类别或支付方不正确");
        }
        if (InjuryRules.CLOSED.equals(c.getStatus())) {
            throw new BizException("已结案，不能再登记费用");
        }
        if (d.getPayDate().isBefore(c.getInjuryTime().toLocalDate())) {
            throw new BizException("支付日期不能早于受伤日期");
        }
        InjuryCost x = new InjuryCost();
        x.setCaseId(caseId);
        x.setCategory(d.getCategory());
        x.setAmount(d.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        x.setPayer(d.getPayer());
        x.setPayDate(d.getPayDate());
        x.setRemark(trim(d.getRemark()));
        x.setRecorderName(me().getNickname());
        costMapper.insert(x);
        return x.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeCost(Long costId) {
        InjuryCost x = costMapper.selectById(costId);
        if (x == null) {
            throw new BizException(404, "费用记录不存在");
        }
        if (InjuryRules.CLOSED.equals(load(x.getCaseId()).getStatus())) {
            throw new BizException("已结案，不能删除费用");
        }
        costMapper.deleteById(costId);
    }

    // ============================== 内部 ==============================

    private void fire(InjuryCase c, InjuryRules.Action a) {
        if (!a.canFire(c.getStatus())) {
            throw new BizException("当前状态【" + InjuryRules.STATUS.get(c.getStatus()) + "】不能" + a.label());
        }
        c.setStatus(a.to());
        caseMapper.updateById(c);
    }

    private static InjuryRules.Deadline deadline(InjuryCase c, LocalDate today) {
        return InjuryRules.applyDeadline(c.getInjuryTime().toLocalDate(), c.getStatus(), today);
    }

    private Map<Long, BigDecimal> totals(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return costMapper.selectList(Wrappers.<InjuryCost>lambdaQuery().in(InjuryCost::getCaseId, ids).select(InjuryCost::getCaseId, InjuryCost::getAmount))
                .stream().collect(Collectors.groupingBy(InjuryCost::getCaseId, Collectors.reducing(BigDecimal.ZERO, InjuryCost::getAmount, BigDecimal::add)));
    }

    private static BigDecimal sum(List<InjuryCost> l) {
        return l.stream().map(InjuryCost::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private InjuryCase load(Long id) {
        InjuryCase c = caseMapper.selectById(id);
        if (c == null) {
            throw new BizException(404, "工伤记录不存在");
        }
        return c;
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
