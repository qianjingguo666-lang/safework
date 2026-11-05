package com.safework.safety.health;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.OhExam;
import com.safework.safety.entity.OhFactor;
import com.safework.safety.entity.OhPostFactor;
import com.safework.safety.entity.PpePost;
import com.safework.safety.entity.PpePostUser;
import com.safework.safety.mapper.OhExamMapper;
import com.safework.safety.mapper.OhFactorMapper;
import com.safework.safety.mapper.OhPostFactorMapper;
import com.safework.safety.mapper.PpePostMapper;
import com.safework.safety.mapper.PpePostUserMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 职业健康：危害因素目录 → 岗位接触的危害因素（岗位复用劳保模块）→ 接害人员 → 体检记录 → 监护台账（下次体检、逾期、异常）。
 * <pre>
 * 岗位（劳保模块）── 人员 ──┐
 *       └── 接触的危害因素 ──┴─▶ 监护台账：周期 = 因素里最短的；最近一次上岗前 / 在岗体检 + 周期 = 下次体检
 *                                   │  体检异常（需复查、疑似职业病、禁忌证）→ 待处理 → 登记处理措施（调岗、复查结果……）
 *                                   └─ 每天提醒：到期前 30 / 7 / 0 天、逾期每 7 天；复查日期到了提醒
 * </pre>
 * 体检结果是个人健康信息：有 health:edit 的职业卫生管理员看全部，其他人只能看自己的。
 */
@Service
@RequiredArgsConstructor
public class HealthService {

    public static final String PERM_LIST = "health:list";
    public static final String PERM_EDIT = "health:edit";
    public static final String LINK = "/safety/health";

    private final OhFactorMapper factorMapper;
    private final OhPostFactorMapper postFactorMapper;
    private final OhExamMapper examMapper;
    private final PpePostMapper postMapper;
    private final PpePostUserMapper postUserMapper;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 危害因素 ==============================

    public List<OhFactor> factors(Boolean enabledOnly) {
        return factorMapper.selectList(Wrappers.<OhFactor>lambdaQuery()
                .eq(Boolean.TRUE.equals(enabledOnly), OhFactor::getStatus, 1)
                .orderByDesc(OhFactor::getStatus)
                .orderByAsc(OhFactor::getCategory)
                .orderByAsc(OhFactor::getName));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveFactor(Long id, HealthDTOs.FactorSave dto) {
        if (!HealthRules.CATEGORIES.containsKey(dto.getCategory())) {
            throw new BizException("危害因素类别不正确");
        }
        String name = dto.getName().trim();
        if (factorMapper.selectCount(Wrappers.<OhFactor>lambdaQuery()
                .eq(OhFactor::getName, name).ne(id != null, OhFactor::getId, id)) > 0) {
            throw new BizException("已经有【" + name + "】了");
        }
        OhFactor f = id == null ? new OhFactor() : loadFactor(id);
        f.setName(name);
        f.setCategory(dto.getCategory());
        f.setCycleMonths(dto.getCycleMonths());
        f.setLimitValue(trim(dto.getLimitValue()));
        f.setExamItems(trim(dto.getExamItems()));
        f.setContraindication(trim(dto.getContraindication()));
        f.setRemark(trim(dto.getRemark()));
        if (id == null) {
            f.setStatus(1);
            factorMapper.insert(f);
        } else {
            factorMapper.updateById(f);
        }
        return f.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void factorStatus(Long id, int status) {
        OhFactor f = loadFactor(id);
        if (status == 0) {
            long used = postFactorMapper.selectCount(Wrappers.<OhPostFactor>lambdaQuery().eq(OhPostFactor::getFactorId, id));
            if (used > 0) {
                throw new BizException("有 " + used + " 个岗位接触【" + f.getName() + "】，先在岗位里去掉");
            }
        }
        f.setStatus(status == 1 ? 1 : 0);
        factorMapper.updateById(f);
    }

    // ============================== 岗位接触的危害因素 ==============================

    public List<HealthVOs.PostFactors> postFactors() {
        List<PpePost> posts = postMapper.selectList(Wrappers.<PpePost>lambdaQuery()
                .orderByDesc(PpePost::getStatus).orderByAsc(PpePost::getName));
        Map<Long, Long> members = postUserMapper.selectList(Wrappers.<PpePostUser>lambdaQuery())
                .stream().collect(Collectors.groupingBy(PpePostUser::getPostId, Collectors.counting()));
        Map<Long, List<OhPostFactor>> links = postFactorMapper.selectList(Wrappers.<OhPostFactor>lambdaQuery())
                .stream().collect(Collectors.groupingBy(OhPostFactor::getPostId));
        Map<Long, OhFactor> factors = factorMap();
        return posts.stream().map(p -> {
            List<OhPostFactor> l = links.getOrDefault(p.getId(), List.of()).stream()
                    .filter(x -> factors.containsKey(x.getFactorId())).toList();
            return new HealthVOs.PostFactors(p.getId(), p.getName(), p.getStatus(), members.getOrDefault(p.getId(), 0L).intValue(),
                    l.stream().map(OhPostFactor::getFactorId).toList(),
                    l.stream().map(x -> factors.get(x.getFactorId()).getName()).toList());
        }).toList();
    }

    /** 设置岗位接触的危害因素（整体替换），台账立即按新的计算 */
    @Transactional(rollbackFor = Exception.class)
    public void savePostFactors(Long postId, List<Long> factorIds) {
        if (postMapper.selectById(postId) == null) {
            throw new BizException(404, "岗位不存在");
        }
        Set<Long> ids = new LinkedHashSet<>(factorIds == null ? List.of() : factorIds);
        Map<Long, OhFactor> factors = factorMap();
        for (Long fid : ids) {
            OhFactor f = factors.get(fid);
            if (f == null) {
                throw new BizException("危害因素不存在");
            }
            if (!Integer.valueOf(1).equals(f.getStatus())) {
                throw new BizException("【" + f.getName() + "】已停用");
            }
        }
        postFactorMapper.delete(Wrappers.<OhPostFactor>lambdaQuery().eq(OhPostFactor::getPostId, postId));
        for (Long fid : ids) {
            OhPostFactor x = new OhPostFactor();
            x.setPostId(postId);
            x.setFactorId(fid);
            postFactorMapper.insert(x);
        }
    }

    // ============================== 监护台账 ==============================

    public HealthVOs.LedgerPage ledger(HealthDTOs.LedgerQuery q) {
        Long only = StpUtil.hasPermission(PERM_EDIT) ? null : StpUtil.getLoginIdAsLong();
        String kw = q.getKeyword();
        String state = q.getState();
        List<HealthVOs.LedgerRow> list = compute(only, q.getPostId(), LocalDate.now()).stream()
                .filter(r -> !StringUtils.hasText(kw) || r.userName().contains(kw) || r.posts().contains(kw) || r.factors().contains(kw))
                .filter(r -> !StringUtils.hasText(state)
                        || ("NEED".equals(state) ? HealthRules.NEVER.equals(r.state()) || HealthRules.OVERDUE.equals(r.state())
                        : "ABNORMAL".equals(state) ? r.openAbnormal() > 0 || r.contraindication() : r.state().equals(state)))
                .toList();
        long from = Math.max(0, (q.getCurrent() - 1) * q.getSize());
        long to = Math.min(list.size(), from + q.getSize());
        return new HealthVOs.LedgerPage(from >= list.size() ? List.of() : list.subList((int) from, (int) to), list.size());
    }

    public HealthVOs.Overview overview() {
        Long only = StpUtil.hasPermission(PERM_EDIT) ? null : StpUtil.getLoginIdAsLong();
        List<HealthVOs.LedgerRow> rows = compute(only, null, LocalDate.now());
        long abnormal = examMapper.selectCount(Wrappers.<OhExam>lambdaQuery()
                .eq(OhExam::getHandleStatus, HealthRules.OPEN)
                .eq(only != null, OhExam::getUserId, only));
        return new HealthVOs.Overview(rows.size(), count(rows, HealthRules.NEVER), count(rows, HealthRules.OVERDUE),
                count(rows, HealthRules.DUE_SOON), (int) abnormal, (int) rows.stream().filter(HealthVOs.LedgerRow::contraindication).count());
    }

    /** 我的职业健康（手机端、首页待办）：不看权限，只返回自己的 */
    public HealthVOs.Mine mine() {
        Long me = StpUtil.getLoginIdAsLong();
        List<HealthVOs.LedgerRow> rows = compute(me, null, LocalDate.now());
        List<OhExam> exams = examMapper.selectList(Wrappers.<OhExam>lambdaQuery()
                .eq(OhExam::getUserId, me)
                .orderByDesc(OhExam::getExamDate)
                .last("LIMIT 50"));
        return new HealthVOs.Mine(rows.isEmpty() ? null : rows.get(0), exams);
    }

    /**
     * 算台账：在启用岗位上、且岗位接触危害因素的人。定时任务也调用（userId 传空看全部）。
     * 排序：要处理的在前（禁忌证、逾期、从没体检、有异常、快到期、正常）。
     */
    public List<HealthVOs.LedgerRow> compute(Long userId, Long postId, LocalDate today) {
        Map<Long, PpePost> posts = postMapper.selectList(Wrappers.<PpePost>lambdaQuery().eq(PpePost::getStatus, 1))
                .stream().collect(Collectors.toMap(PpePost::getId, Function.identity()));
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<Long, OhFactor> factors = factorMap();
        Map<Long, List<OhFactor>> postFactors = new HashMap<>();
        postFactorMapper.selectList(Wrappers.<OhPostFactor>lambdaQuery().in(OhPostFactor::getPostId, posts.keySet()))
                .forEach(x -> {
                    OhFactor f = factors.get(x.getFactorId());
                    if (f != null && Integer.valueOf(1).equals(f.getStatus())) {
                        postFactors.computeIfAbsent(x.getPostId(), k -> new ArrayList<>()).add(f);
                    }
                });
        if (postFactors.isEmpty()) {
            return List.of();
        }
        List<PpePostUser> members = postUserMapper.selectList(Wrappers.<PpePostUser>lambdaQuery()
                .in(PpePostUser::getPostId, postFactors.keySet())
                .eq(postId != null, PpePostUser::getPostId, postId)
                .eq(userId != null, PpePostUser::getUserId, userId));
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Set<String>> postNames = new HashMap<>();
        Map<Long, Map<Long, OhFactor>> userFactors = new HashMap<>();
        for (PpePostUser m : members) {
            names.put(m.getUserId(), m.getUserName());
            postNames.computeIfAbsent(m.getUserId(), k -> new LinkedHashSet<>()).add(posts.get(m.getPostId()).getName());
            postFactors.get(m.getPostId()).forEach(f -> userFactors.computeIfAbsent(m.getUserId(), k -> new LinkedHashMap<>()).put(f.getId(), f));
        }
        // 每人最近一次上岗前 / 在岗体检，以及未处理的异常数
        Map<Long, OhExam> last = new HashMap<>();
        Map<Long, Integer> open = new HashMap<>();
        examMapper.selectList(Wrappers.<OhExam>lambdaQuery().in(OhExam::getUserId, names.keySet())).forEach(e -> {
            if (HealthRules.countsForCycle(e.getExamType())) {
                last.merge(e.getUserId(), e, (a, b) -> a.getExamDate().isAfter(b.getExamDate()) ? a : b);
            }
            if (HealthRules.OPEN.equals(e.getHandleStatus())) {
                open.merge(e.getUserId(), 1, Integer::sum);
            }
        });

        List<HealthVOs.LedgerRow> rows = new ArrayList<>();
        names.forEach((uid, name) -> {
            List<OhFactor> fs = new ArrayList<>(userFactors.get(uid).values());
            int cycle = HealthRules.cycle(fs.stream().map(OhFactor::getCycleMonths).toList());
            OhExam e = last.get(uid);
            LocalDate next = e == null ? null : HealthRules.nextExam(e.getExamDate(), cycle);
            rows.add(new HealthVOs.LedgerRow(uid, name, String.join("、", postNames.get(uid)),
                    fs.stream().map(OhFactor::getName).collect(Collectors.joining("、")), cycle,
                    e == null ? null : e.getExamDate(), e == null ? null : e.getConclusion(), next,
                    next == null ? null : ChronoUnit.DAYS.between(today, next), HealthRules.state(next, today),
                    e != null && "CONTRAINDICATION".equals(e.getConclusion()), open.getOrDefault(uid, 0)));
        });
        rows.sort(Comparator.comparingInt(HealthService::priority).thenComparing(HealthVOs.LedgerRow::userName));
        return rows;
    }

    private static int priority(HealthVOs.LedgerRow r) {
        if (r.contraindication()) {
            return 0;
        }
        return switch (r.state()) {
            case HealthRules.OVERDUE -> 1;
            case HealthRules.NEVER -> 2;
            default -> r.openAbnormal() > 0 ? 3 : HealthRules.DUE_SOON.equals(r.state()) ? 4 : 5;
        };
    }

    // ============================== 体检记录 ==============================

    public Page<OhExam> examPage(HealthDTOs.ExamQuery q) {
        boolean all = StpUtil.hasPermission(PERM_EDIT);
        String kw = q.getKeyword();
        LambdaQueryWrapper<OhExam> w = Wrappers.<OhExam>lambdaQuery()
                .eq(!all, OhExam::getUserId, StpUtil.getLoginIdAsLong())
                .eq(all && q.getUserId() != null, OhExam::getUserId, q.getUserId())
                .eq(StringUtils.hasText(q.getConclusion()), OhExam::getConclusion, q.getConclusion())
                .eq(StringUtils.hasText(q.getExamType()), OhExam::getExamType, q.getExamType())
                .eq(Boolean.TRUE.equals(q.getOpen()), OhExam::getHandleStatus, HealthRules.OPEN)
                .and(StringUtils.hasText(kw), x -> x.like(OhExam::getUserName, kw).or().like(OhExam::getOrg, kw).or().like(OhExam::getFactors, kw))
                .orderByDesc(OhExam::getExamDate)
                .orderByDesc(OhExam::getCreateTime);
        return examMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), w);
    }

    /**
     * 登记体检结果。异常结论（需复查、疑似职业病、禁忌证）进入“待处理”，并通知本人和职业卫生管理员；
     * 职业禁忌证要求调离接害岗位，在台账里标红，直到调岗（从岗位里移除）或者新的体检结论正常。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveExam(HealthDTOs.ExamSave dto) {
        LocalDate today = LocalDate.now();
        String error = HealthRules.checkExam(dto.getExamType(), dto.getConclusion(), dto.getExamDate(), dto.getRecheckDate(), today);
        if (error != null) {
            throw new BizException(error);
        }
        SysUser user = userService.getById(dto.getUserId());
        // 体检时接触的危害因素，存名称快照（以后岗位变了也知道当时查的是什么）
        List<HealthVOs.LedgerRow> rows = compute(user.getId(), null, today);
        OhExam e = new OhExam();
        e.setUserId(user.getId());
        e.setUserName(user.getNickname());
        e.setExamType(dto.getExamType());
        e.setExamDate(dto.getExamDate());
        e.setOrg(trim(dto.getOrg()));
        e.setReportNo(trim(dto.getReportNo()));
        e.setConclusion(dto.getConclusion());
        e.setFactors(rows.isEmpty() ? null : rows.get(0).factors());
        e.setDetail(trim(dto.getDetail()));
        e.setRecheckDate(dto.getRecheckDate());
        boolean abnormal = HealthRules.isAbnormal(dto.getConclusion());
        e.setHandleStatus(abnormal ? HealthRules.OPEN : HealthRules.NONE);
        e.setRecorderName(userService.getById(StpUtil.getLoginIdAsLong()).getNickname());
        examMapper.insert(e);

        String conclusion = HealthRules.CONCLUSIONS.get(dto.getConclusion());
        String type = HealthRules.EXAM_TYPES.get(dto.getExamType());
        messageService.send(List.of(user.getId()), MessageDraft.of(abnormal ? MessageCategory.WARN : MessageCategory.NOTICE, "HEALTH", null,
                "你的" + type + "职业健康检查结果：" + conclusion,
                dto.getExamDate() + " 体检" + (e.getOrg() == null ? "" : "（" + e.getOrg() + "）")
                        + (dto.getRecheckDate() != null ? "，请在 " + dto.getRecheckDate() + " 前复查" : "")
                        + (abnormal ? "。职业卫生管理员会联系你安排后续处理" : ""), LINK));
        if (abnormal) {
            List<Long> admins = new ArrayList<>(messageService.listPermissionUsers(PERM_EDIT));
            admins.remove(StpUtil.getLoginIdAsLong());
            messageService.send(admins, MessageDraft.of(MessageCategory.WARN, "HEALTH", null,
                    "职业健康检查异常：" + user.getNickname() + " " + conclusion,
                    type + "体检，接触：" + (e.getFactors() == null ? "无" : e.getFactors())
                            + ("CONTRAINDICATION".equals(dto.getConclusion()) ? "。有职业禁忌证，应调离接害岗位" : "") + "。请登记处理措施", LINK));
        }
        return e.getId();
    }

    /** 异常处理：登记处理措施（复查结果、调岗、申请职业病诊断……）后关闭 */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String measure) {
        OhExam e = examMapper.selectById(id);
        if (e == null) {
            throw new BizException(404, "体检记录不存在");
        }
        if (!HealthRules.OPEN.equals(e.getHandleStatus())) {
            throw new BizException("这条体检记录不需要处理，或者已经处理过了");
        }
        e.setHandleStatus(HealthRules.CLOSED);
        e.setHandleMeasure(measure.trim());
        e.setHandlerName(userService.getById(StpUtil.getLoginIdAsLong()).getNickname());
        e.setHandleTime(LocalDateTime.now());
        examMapper.updateById(e);
    }

    // ============================== 内部方法 ==============================

    private Map<Long, OhFactor> factorMap() {
        return factorMapper.selectList(Wrappers.<OhFactor>lambdaQuery())
                .stream().collect(Collectors.toMap(OhFactor::getId, Function.identity()));
    }

    private static int count(List<HealthVOs.LedgerRow> rows, String state) {
        return (int) rows.stream().filter(r -> state.equals(r.state())).count();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private OhFactor loadFactor(Long id) {
        OhFactor f = factorMapper.selectById(id);
        if (f == null) {
            throw new BizException(404, "危害因素不存在");
        }
        return f;
    }
}
