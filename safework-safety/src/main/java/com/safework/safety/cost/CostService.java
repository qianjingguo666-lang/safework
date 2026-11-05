package com.safework.safety.cost;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.CostBudget;
import com.safework.safety.entity.CostExpense;
import com.safework.safety.entity.CostPlan;
import com.safework.safety.entity.Hazard;
import com.safework.safety.excel.CostExcelRow;
import com.safework.safety.mapper.CostBudgetMapper;
import com.safework.safety.mapper.CostExpenseMapper;
import com.safework.safety.mapper.CostPlanMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.system.entity.SysUser;
import com.safework.system.excel.ExcelHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安全投入台账（安全生产费用）：
 * <pre>
 * 年度计划：上年营业收入 ──超额累退──▶ 本年应提（逐月平均提取）+ 上年结转 = 当年可用；按 10 个使用类别分预算
 *      │
 * 支出台账：一笔一行（类别、金额、日期、凭证号、发票附件，可关联隐患 = 隐患整改资金）；登错了作废留痕
 *      │
 * 统计：每类预算 / 已用 / 超预算，每月累计支出 vs 累计可用，余额为负提示年末补提，使用进度偏慢提示
 * </pre>
 * report() 没有用登录信息，定时任务、大屏、智能体都可以调用。
 */
@Service
@RequiredArgsConstructor
public class CostService {

    public static final String PERM_LIST = "cost:list";
    public static final String PERM_EDIT = "cost:edit";
    public static final String BIZ_TYPE = "COST";
    public static final String TAG_VOUCHER = "VOUCHER";
    public static final String LINK = "/safety/cost";
    private static final int MAX_EXPORT = 5000;

    private final CostPlanMapper planMapper;
    private final CostBudgetMapper budgetMapper;
    private final CostExpenseMapper expenseMapper;
    private final HazardMapper hazardMapper;
    private final SerialNoGenerator serialNo;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 年度报表 ==============================

    public CostVOs.Report report(int year) {
        LocalDate today = LocalDate.now();
        CostPlan plan = plan(year);
        CostPlan last = plan(year - 1);
        Map<String, BigDecimal> budgets = plan == null ? Map.of() : budgetMap(plan.getId());
        List<CostExpense> expenses = expenseMapper.selectList(Wrappers.<CostExpense>lambdaQuery()
                .eq(CostExpense::getYear, year).eq(CostExpense::getStatus, 1));

        BigDecimal accrue = plan == null ? BigDecimal.ZERO : plan.getAccrueAmount();
        BigDecimal carry = plan == null ? BigDecimal.ZERO : plan.getCarryOver();
        BigDecimal available = CostRules.money(carry.add(accrue));
        BigDecimal used = CostRules.money(sum(expenses));
        BigDecimal balance = available.subtract(used);
        Double usageRate = CostRules.rate(used, available);
        int monthsPassed = year < today.getYear() ? 12 : year > today.getYear() ? 0 : today.getMonthValue();
        double timeProgress = CostRules.timeProgress(year, today);
        boolean lagging = plan != null && CostRules.lagging(usageRate, timeProgress);
        boolean canPause = plan != null && last != null && CostRules.canPause(carry, last.getAccrueAmount());

        Map<String, List<CostExpense>> byCat = expenses.stream().collect(Collectors.groupingBy(CostExpense::getCategory));
        List<CostVOs.CategoryRow> cats = new ArrayList<>();
        for (CostRules.Category c : CostRules.CATEGORY_LIST) {
            List<CostExpense> l = byCat.getOrDefault(c.code(), List.of());
            BigDecimal b = budgets.getOrDefault(c.code(), BigDecimal.ZERO);
            BigDecimal u = CostRules.money(sum(l));
            cats.add(new CostVOs.CategoryRow(c.code(), c.label(), c.description(), b, u, l.size(), CostRules.rate(u, b),
                    plan == null ? CostRules.NORMAL : CostRules.categoryState(b, u)));
        }

        Map<Integer, BigDecimal> perMonth = expenses.stream().collect(Collectors.groupingBy(e -> e.getExpenseDate().getMonthValue(),
                Collectors.reducing(BigDecimal.ZERO, CostExpense::getAmount, BigDecimal::add)));
        List<CostVOs.Month> months = new ArrayList<>();
        BigDecimal cum = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            BigDecimal mu = CostRules.money(perMonth.getOrDefault(m, BigDecimal.ZERO));
            cum = cum.add(mu);
            BigDecimal cumAvail = carry.add(CostRules.accruedByMonth(accrue, m));
            boolean future = m > monthsPassed;
            months.add(new CostVOs.Month(m, future ? null : mu, future ? null : CostRules.money(cum), plan == null ? null : CostRules.money(cumAvail)));
        }

        int over = (int) cats.stream().filter(c -> CostRules.OVER.equals(c.state()) || CostRules.UNPLANNED.equals(c.state())).count();
        List<String> alerts = new ArrayList<>();
        if (plan == null) {
            alerts.add(year + " 年还没有编制安全生产费用计划");
        } else {
            cats.stream().filter(c -> CostRules.OVER.equals(c.state())).forEach(c -> alerts.add("“" + c.label() + "”超预算 "
                    + CostRules.yuan(c.used().subtract(c.budget())) + " 元"));
            cats.stream().filter(c -> CostRules.UNPLANNED.equals(c.state())).forEach(c -> alerts.add("“" + c.label() + "”没有预算，已支出 "
                    + CostRules.yuan(c.used()) + " 元"));
            if (balance.signum() < 0) {
                alerts.add("已使用超过当年可用额度 " + CostRules.yuan(balance.negate()) + " 元，按规定年末要补提");
            }
            if (lagging) {
                alerts.add("使用进度偏慢：时间过去 " + timeProgress + "%，费用只用了 " + usageRate + "%");
            }
            if (canPause) {
                alerts.add("上年结转已达到上年应提金额的 3 倍，按规定可以暂停提取");
            }
        }
        BigDecimal hazardUsed = CostRules.money(sum(expenses.stream().filter(e -> e.getHazardId() != null).toList()));
        LocalDate asOf = year == today.getYear() ? today : year < today.getYear() ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
        return new CostVOs.Report(year, asOf, plan != null, plan, plan == null ? CostRules.accrue(BigDecimal.ZERO) : CostRules.accrue(plan.getRevenue()),
                available, used, balance, usageRate, CostRules.accruedByMonth(accrue, monthsPassed), timeProgress, lagging, canPause,
                over, expenses.size(), hazardUsed, cats, months, alerts);
    }

    // ============================== 年度计划 ==============================

    public CostVOs.PlanForm planForm(int year) {
        CostPlan plan = plan(year);
        Map<String, BigDecimal> budgets = plan == null ? Map.of() : budgetMap(plan.getId());
        BigDecimal suggested = BigDecimal.ZERO;
        CostPlan last = plan(year - 1);
        if (last != null) {
            BigDecimal lastUsed = sum(expenseMapper.selectList(Wrappers.<CostExpense>lambdaQuery()
                    .select(CostExpense::getAmount).eq(CostExpense::getYear, year - 1).eq(CostExpense::getStatus, 1)));
            BigDecimal b = last.getCarryOver().add(last.getAccrueAmount()).subtract(lastUsed);
            suggested = CostRules.money(b.max(BigDecimal.ZERO));
        }
        List<CostVOs.BudgetRow> rows = CostRules.CATEGORY_LIST.stream()
                .map(c -> new CostVOs.BudgetRow(c.code(), c.label(), c.description(), budgets.getOrDefault(c.code(), BigDecimal.ZERO))).toList();
        return new CostVOs.PlanForm(year, plan, suggested, rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public CostVOs.SaveResult savePlan(CostDTOs.PlanSave dto) {
        List<CostDTOs.Budget> budgets = dto.getBudgets() == null ? List.of() : dto.getBudgets();
        for (CostDTOs.Budget b : budgets) {
            if (!CostRules.CATEGORIES.containsKey(b.getCategory())) {
                throw new BizException("使用类别不正确：" + b.getCategory());
            }
        }
        if (budgets.stream().map(CostDTOs.Budget::getCategory).distinct().count() != budgets.size()) {
            throw new BizException("同一个类别只能填一次预算");
        }
        CostPlan p = plan(dto.getYear());
        boolean create = p == null;
        if (create) {
            p = new CostPlan();
            p.setYear(dto.getYear());
        }
        p.setRevenue(dto.getRevenue().setScale(2, java.math.RoundingMode.HALF_UP));
        p.setAccrueAmount(CostRules.accrue(dto.getRevenue()).total());
        p.setCarryOver(CostRules.money(dto.getCarryOver()));
        p.setRemark(trim(dto.getRemark()));
        p.setUpdaterName(me().getNickname());
        if (create) {
            planMapper.insert(p);
        } else {
            planMapper.updateById(p);
            budgetMapper.delete(Wrappers.<CostBudget>lambdaQuery().eq(CostBudget::getPlanId, p.getId()));
        }
        BigDecimal total = BigDecimal.ZERO;
        for (CostDTOs.Budget b : budgets) {
            if (b.getAmount().signum() == 0) {
                continue;
            }
            CostBudget cb = new CostBudget();
            cb.setPlanId(p.getId());
            cb.setYear(p.getYear());
            cb.setCategory(b.getCategory());
            cb.setAmount(CostRules.money(b.getAmount()));
            budgetMapper.insert(cb);
            total = total.add(cb.getAmount());
        }
        return new CostVOs.SaveResult(p.getId(), CostRules.budgetWarning(total, p.getCarryOver().add(p.getAccrueAmount())));
    }

    // ============================== 支出台账 ==============================

    public CostVOs.ExpensePage expenses(CostDTOs.ExpenseQuery q) {
        Page<CostExpense> page = expenseMapper.selectPage(Page.of(q.getCurrent(), Math.min(q.getSize(), 100)),
                filter(q).orderByDesc(CostExpense::getExpenseDate).orderByDesc(CostExpense::getCreateTime));
        BigDecimal total = sum(expenseMapper.selectList(filter(q).select(CostExpense::getAmount).eq(CostExpense::getStatus, 1)));
        return new CostVOs.ExpensePage(page.getRecords(), page.getTotal(), CostRules.money(total));
    }

    public CostExpense expense(Long id) {
        CostExpense e = expenseMapper.selectById(id);
        if (e == null) {
            throw new BizException(404, "支出记录不存在");
        }
        return e;
    }

    @Transactional(rollbackFor = Exception.class)
    public CostVOs.SaveResult saveExpense(Long id, CostDTOs.ExpenseSave dto) {
        if (!CostRules.CATEGORIES.containsKey(dto.getCategory())) {
            throw new BizException("使用类别不正确");
        }
        String err = CostRules.checkExpenseDate(dto.getExpenseDate(), LocalDate.now());
        if (err != null) {
            throw new BizException(err);
        }
        CostExpense e = id == null ? new CostExpense() : expense(id);
        if (id != null && !Integer.valueOf(1).equals(e.getStatus())) {
            throw new BizException("已作废的记录不能修改");
        }
        int year = dto.getExpenseDate().getYear();
        BigDecimal before = categoryUsed(year, dto.getCategory(), id);
        e.setYear(year);
        e.setCategory(dto.getCategory());
        e.setTitle(dto.getTitle().trim());
        e.setAmount(CostRules.money(dto.getAmount()));
        e.setExpenseDate(dto.getExpenseDate());
        e.setDeptName(trim(dto.getDeptName()));
        e.setPayee(trim(dto.getPayee()));
        e.setVoucherNo(trim(dto.getVoucherNo()));
        e.setRemark(trim(dto.getRemark()));
        if (dto.getHazardId() != null) {
            Hazard h = hazardMapper.selectById(dto.getHazardId());
            if (h == null) {
                throw new BizException("关联的隐患不存在");
            }
            e.setHazardId(h.getId());
            e.setHazardNo(h.getHazardNo());
            e.setHazardTitle(h.getTitle());
        } else {
            e.setHazardId(null);
            e.setHazardNo(null);
            e.setHazardTitle(null);
        }
        if (id == null) {
            SysUser me = me();
            e.setExpenseNo(serialNo.next("AF"));
            e.setRecorderId(me.getId());
            e.setRecorderName(me.getNickname());
            e.setStatus(1);
            expenseMapper.insert(e);
        } else {
            expenseMapper.updateById(e);
        }
        return new CostVOs.SaveResult(e.getId(), overBudgetWarning(e, before));
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidExpense(Long id, String reason) {
        CostExpense e = expense(id);
        if (!Integer.valueOf(1).equals(e.getStatus())) {
            throw new BizException("这条记录已经作废了");
        }
        e.setStatus(0);
        e.setVoidReason(reason.trim());
        e.setVoiderName(me().getNickname());
        e.setVoidTime(LocalDateTime.now());
        expenseMapper.updateById(e);
    }

    public void export(CostDTOs.ExpenseQuery q, HttpServletResponse response) {
        List<CostExcelRow> rows = expenseMapper.selectPage(Page.of(1, MAX_EXPORT, false),
                filter(q).orderByAsc(CostExpense::getExpenseDate).orderByAsc(CostExpense::getCreateTime)).getRecords().stream().map(e -> {
            CostExcelRow r = new CostExcelRow();
            r.setExpenseNo(e.getExpenseNo());
            r.setExpenseDate(e.getExpenseDate().toString());
            r.setCategory(CostRules.CATEGORIES.get(e.getCategory()).label());
            r.setTitle(e.getTitle());
            r.setAmount(e.getAmount());
            r.setDeptName(e.getDeptName());
            r.setPayee(e.getPayee());
            r.setVoucherNo(e.getVoucherNo());
            r.setHazard(e.getHazardNo() == null ? null : e.getHazardNo() + " " + e.getHazardTitle());
            r.setRecorderName(e.getRecorderName());
            r.setStatus(Integer.valueOf(1).equals(e.getStatus()) ? "有效" : "作废：" + e.getVoidReason());
            r.setRemark(e.getRemark());
            return r;
        }).toList();
        ExcelHelper.write(response, "安全费用支出台账", CostExcelRow.class, rows);
    }

    // ============================== 内部 ==============================

    /** 这一笔让分类从“没超”变成“超预算”：返回提示，并通知其他管理员 */
    private String overBudgetWarning(CostExpense e, BigDecimal before) {
        CostPlan plan = plan(e.getYear());
        if (plan == null) {
            return e.getYear() + " 年还没有编制安全生产费用计划，这笔支出暂时没有预算可以对照";
        }
        BigDecimal budget = budgetMap(plan.getId()).getOrDefault(e.getCategory(), BigDecimal.ZERO);
        BigDecimal after = before.add(e.getAmount());
        String label = CostRules.CATEGORIES.get(e.getCategory()).label();
        if (after.compareTo(budget) <= 0) {
            return null;
        }
        String text = budget.signum() == 0 ? "“" + label + "”没有安排预算，" + e.getYear() + " 年已支出 " + CostRules.yuan(after) + " 元"
                : "“" + label + "”" + e.getYear() + " 年已支出 " + CostRules.yuan(after) + " 元，超过预算 " + CostRules.yuan(budget) + " 元";
        if (before.compareTo(budget) <= 0) {
            List<Long> to = messageService.listPermissionUsers(PERM_EDIT).stream().filter(u -> !u.equals(StpUtil.getLoginIdAsLong())).toList();
            messageService.send(to, MessageDraft.of(MessageCategory.WARN, BIZ_TYPE, e.getId(), "安全费用超预算：" + label,
                    text + "。最新一笔：" + e.getTitle() + " " + CostRules.yuan(e.getAmount()) + " 元（" + e.getRecorderName() + " 登记）", LINK));
        }
        return text;
    }

    /** 某年某类已使用（不含 excludeId，修改时用） */
    private BigDecimal categoryUsed(int year, String category, Long excludeId) {
        return sum(expenseMapper.selectList(Wrappers.<CostExpense>lambdaQuery().select(CostExpense::getAmount)
                .eq(CostExpense::getYear, year).eq(CostExpense::getCategory, category).eq(CostExpense::getStatus, 1)
                .ne(excludeId != null, CostExpense::getId, excludeId)));
    }

    private LambdaQueryWrapper<CostExpense> filter(CostDTOs.ExpenseQuery q) {
        String kw = q.getKeyword() == null ? null : q.getKeyword().trim();
        return Wrappers.<CostExpense>lambdaQuery()
                .eq(q.getYear() != null, CostExpense::getYear, q.getYear())
                .eq(StringUtils.hasText(q.getCategory()), CostExpense::getCategory, q.getCategory())
                .eq(q.getStatus() != null, CostExpense::getStatus, q.getStatus())
                .eq(q.getHazardId() != null, CostExpense::getHazardId, q.getHazardId())
                .and(StringUtils.hasText(kw), w -> w.like(CostExpense::getTitle, kw).or().like(CostExpense::getExpenseNo, kw)
                        .or().like(CostExpense::getPayee, kw).or().like(CostExpense::getVoucherNo, kw).or().like(CostExpense::getDeptName, kw)
                        .or().like(CostExpense::getHazardNo, kw));
    }

    private CostPlan plan(int year) {
        return planMapper.selectOne(Wrappers.<CostPlan>lambdaQuery().eq(CostPlan::getYear, year).last("LIMIT 1"));
    }

    private Map<String, BigDecimal> budgetMap(Long planId) {
        return budgetMapper.selectList(Wrappers.<CostBudget>lambdaQuery().eq(CostBudget::getPlanId, planId)).stream()
                .collect(Collectors.toMap(CostBudget::getCategory, CostBudget::getAmount, BigDecimal::add));
    }

    private static BigDecimal sum(List<CostExpense> l) {
        return l.stream().map(CostExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
