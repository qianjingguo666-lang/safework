package com.safework.safety.check;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.HazardReportDTO;
import com.safework.safety.entity.CheckPlan;
import com.safework.safety.entity.CheckTask;
import com.safework.safety.entity.CheckTemplate;
import com.safework.safety.entity.Hazard;
import com.safework.safety.mapper.CheckPlanMapper;
import com.safework.safety.mapper.CheckTaskMapper;
import com.safework.safety.mapper.CheckTemplateMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.service.HazardService;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 安全检查：检查表模板 → 检查计划（周期、检查人、区域）→ 按周期生成检查任务 → 现场逐项打勾 → 不合格项转隐患。
 * <pre>
 * 检查表（检查项）─┐
 *                 ├─▶ 检查计划 ──每天 7:00 / 新建计划时──▶ 检查任务（待检查）──逐项检查──▶ 已完成
 * 检查人、周期 ───┘                                          │ 过了截止日期 → 逾期提醒          └─ 不合格项 → 隐患（指派、整改、验收闭环）
 * </pre>
 * 数据权限：有 check:edit 的安全员管理模板、计划，看全部任务；其他人只看、只能检查分给自己的任务。
 */
@Service
@RequiredArgsConstructor
public class CheckService {

    public static final String PERM_LIST = "check:list";
    public static final String PERM_EDIT = "check:edit";
    public static final String BIZ_TYPE = "CHECK";

    private static final TypeReference<List<CheckItem>> ITEMS = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> RESULTS = new TypeReference<>() {
    };

    private final CheckTemplateMapper templateMapper;
    private final CheckPlanMapper planMapper;
    private final CheckTaskMapper taskMapper;
    private final HazardService hazardService;
    private final HazardMapper hazardMapper;
    private final UserService userService;
    private final MessageService messageService;
    private final SerialNoGenerator serialNo;
    private final ObjectMapper json = new ObjectMapper();

    // ============================== 检查表模板 ==============================

    public List<CheckTemplate> templates(Boolean enabledOnly) {
        return templateMapper.selectList(Wrappers.<CheckTemplate>lambdaQuery()
                .eq(Boolean.TRUE.equals(enabledOnly), CheckTemplate::getStatus, 1)
                .orderByDesc(CheckTemplate::getStatus)
                .orderByAsc(CheckTemplate::getCheckType)
                .orderByDesc(CheckTemplate::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveTemplate(Long id, TemplateSaveDTO dto) {
        if (!CheckRules.CHECK_TYPES.containsKey(dto.getCheckType())) {
            throw new BizException("检查类型不正确");
        }
        List<CheckItem> items = dto.getItems().stream()
                .map(i -> i == null ? null : new CheckItem(trim(i.content()), trim(i.standard()),
                        StringUtils.hasText(i.category()) ? i.category() : null))
                .toList();
        String error = CheckRules.checkItems(items);
        if (error != null) {
            throw new BizException(error);
        }
        String name = dto.getName().trim();
        if (templateMapper.selectCount(Wrappers.<CheckTemplate>lambdaQuery()
                .eq(CheckTemplate::getName, name).ne(id != null, CheckTemplate::getId, id)) > 0) {
            throw new BizException("已经有叫【" + name + "】的检查表了");
        }
        CheckTemplate t = id == null ? new CheckTemplate() : loadTemplate(id);
        t.setName(name);
        t.setCheckType(dto.getCheckType());
        t.setItems(write(items));
        t.setItemCount(items.size());
        t.setRemark(trim(dto.getRemark()));
        if (id == null) {
            t.setStatus(1);
            templateMapper.insert(t);
        } else {
            templateMapper.updateById(t);
            // 计划上冗余了模板名称，一起改
            planMapper.update(null, Wrappers.<CheckPlan>lambdaUpdate()
                    .set(CheckPlan::getTemplateName, name).set(CheckPlan::getCheckType, dto.getCheckType())
                    .eq(CheckPlan::getTemplateId, id));
        }
        return t.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void templateStatus(Long id, int status) {
        CheckTemplate t = loadTemplate(id);
        if (status == 0) {
            Long used = planMapper.selectCount(Wrappers.<CheckPlan>lambdaQuery()
                    .eq(CheckPlan::getTemplateId, id).eq(CheckPlan::getStatus, 1));
            if (used > 0) {
                throw new BizException("有 " + used + " 个启用的检查计划在用这张检查表，先停用计划");
            }
        }
        t.setStatus(status == 1 ? 1 : 0);
        templateMapper.updateById(t);
    }

    // ============================== 检查计划 ==============================

    public List<CheckPlan> plans() {
        return planMapper.selectList(Wrappers.<CheckPlan>lambdaQuery()
                .orderByDesc(CheckPlan::getStatus)
                .orderByDesc(CheckPlan::getCreateTime));
    }

    /** 新建 / 修改计划。开始日期已经到了，立即生成第一张任务（不用等第二天早上的定时任务） */
    @Transactional(rollbackFor = Exception.class)
    public Long savePlan(Long id, PlanSaveDTO dto) {
        if (!CheckRules.CYCLES.containsKey(dto.getCycle())) {
            throw new BizException("检查周期不正确");
        }
        LocalDate today = LocalDate.now();
        if (dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BizException("结束日期不能早于开始日期");
        }
        if (dto.getEndDate() != null && dto.getEndDate().isBefore(today)) {
            throw new BizException("结束日期已经过了");
        }
        CheckTemplate t = loadTemplate(dto.getTemplateId());
        if (!Integer.valueOf(1).equals(t.getStatus())) {
            throw new BizException("检查表【" + t.getName() + "】已停用");
        }
        SysUser checker = userService.getById(dto.getCheckerId());
        if (!Integer.valueOf(1).equals(checker.getStatus())) {
            throw new BizException("【" + checker.getNickname() + "】的账号已停用，不能做检查人");
        }
        CheckPlan p = id == null ? new CheckPlan() : loadPlan(id);
        boolean reschedule = id == null || !Objects.equals(p.getStartDate(), dto.getStartDate()) || !Objects.equals(p.getCycle(), dto.getCycle());
        p.setName(dto.getName().trim());
        p.setTemplateId(t.getId());
        p.setTemplateName(t.getName());
        p.setCheckType(t.getCheckType());
        p.setCycle(dto.getCycle());
        p.setCheckerId(checker.getId());
        p.setCheckerName(checker.getNickname());
        p.setLocation(dto.getLocation().trim());
        p.setStartDate(dto.getStartDate());
        p.setEndDate(dto.getEndDate());
        p.setRemark(trim(dto.getRemark()));
        if (reschedule) {
            // 新建，或者改了开始日期 / 周期：从开始日期重新排（已经生成的任务不受影响，同一周期不会重复生成）
            p.setNextDate(dto.getStartDate());
        }
        if (id == null) {
            p.setStatus(1);
            planMapper.insert(p);
        } else {
            planMapper.updateById(p);
        }
        if (Integer.valueOf(1).equals(p.getStatus())) {
            generate(p, today);
        }
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void planStatus(Long id, int status) {
        CheckPlan p = loadPlan(id);
        p.setStatus(status == 1 ? 1 : 0);
        planMapper.updateById(p);
        if (status == 1) {
            loadTemplate(p.getTemplateId());
            generate(p, LocalDate.now());
        }
    }

    /**
     * 给一个计划生成“今天所在周期”的任务（定时任务、新建计划都调用这里）。
     * 同一个计划同一个周期有唯一索引：定时任务和页面同时触发、或者多台服务器同时执行，也只会有一张。
     *
     * @return 生成的任务；不需要生成返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckTask generate(CheckPlan p, LocalDate today) {
        LocalDate period = CheckRules.periodToGenerate(p.getCycle(), p.getNextDate(), p.getEndDate(), today);
        if (period == null) {
            if (p.getNextDate() != null && p.getEndDate() != null && p.getNextDate().isAfter(p.getEndDate())) {
                // 计划已经执行完了
                p.setNextDate(null);
                planMapper.updateById(p);
            }
            return null;
        }
        CheckTemplate t = templateMapper.selectById(p.getTemplateId());
        if (t == null || !Integer.valueOf(1).equals(t.getStatus())) {
            return null;
        }
        CheckTask task = new CheckTask();
        task.setTaskNo(serialNo.next("JC"));
        task.setPlanId(p.getId());
        task.setPlanName(p.getName());
        task.setTemplateName(t.getName());
        task.setCheckType(t.getCheckType());
        task.setLocation(p.getLocation());
        task.setCheckerId(p.getCheckerId());
        task.setCheckerName(p.getCheckerName());
        task.setPeriodStart(period);
        task.setDueDate(CheckRules.due(p.getCycle(), period, p.getEndDate()));
        task.setStatus(CheckRules.PENDING);
        task.setItems(t.getItems());
        task.setItemCount(t.getItemCount());
        task.setFailCount(0);
        task.setHazardCount(0);
        boolean created = true;
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // 这个周期已经生成过了
            created = false;
        }
        LocalDate next = CheckRules.next(p.getCycle(), period);
        p.setNextDate(next != null && (p.getEndDate() == null || !next.isAfter(p.getEndDate())) ? next : null);
        planMapper.updateById(p);
        if (!created) {
            return null;
        }
        messageService.send(List.of(p.getCheckerId()), MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, task.getId(),
                "新的检查任务：" + p.getName(),
                "检查区域：" + p.getLocation() + "，检查表《" + t.getName() + "》共 " + t.getItemCount() + " 项，请在 "
                        + task.getDueDate() + " 前完成", link(task.getId())));
        return task;
    }

    // ============================== 检查任务 ==============================

    public Page<CheckTask> page(TaskQueryDTO q) {
        LocalDate today = LocalDate.now();
        String kw = q.getKeyword();
        LambdaQueryWrapper<CheckTask> w = scope(q.getMine())
                .eq(q.getPlanId() != null, CheckTask::getPlanId, q.getPlanId())
                .and(StringUtils.hasText(kw), x -> x.like(CheckTask::getTaskNo, kw).or().like(CheckTask::getPlanName, kw)
                        .or().like(CheckTask::getLocation, kw).or().like(CheckTask::getCheckerName, kw));
        stateFilter(w, q.getState(), today);
        // 待检查的在前（越早到期越靠前），已完成的按完成时间倒序
        w.last("ORDER BY status = 'DONE', CASE WHEN status = 'DONE' THEN NULL ELSE due_date END, finish_time DESC, id DESC");
        Page<CheckTask> page = taskMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), w);
        page.getRecords().forEach(t -> {
            t.setState(CheckRules.state(t.getStatus(), t.getDueDate(), today));
            // 列表不需要检查项明细
            t.setItems(null);
            t.setResults(null);
        });
        return page;
    }

    public CheckVOs.Overview overview() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        int pending = count(stateFilter(scope(false), CheckRules.PENDING, today));
        int overdue = count(stateFilter(scope(false), CheckRules.OVERDUE, today));
        List<CheckTask> monthDone = taskMapper.selectList(scope(false)
                .eq(CheckTask::getStatus, CheckRules.DONE)
                .ge(CheckTask::getFinishTime, monthStart.atStartOfDay())
                .select(CheckTask::getFailCount, CheckTask::getHazardCount));
        // 本月已经到期的任务里，按时完成的比例
        List<CheckTask> dueThisMonth = taskMapper.selectList(scope(false)
                .between(CheckTask::getDueDate, monthStart, today.minusDays(1))
                .select(CheckTask::getStatus, CheckTask::getDueDate, CheckTask::getFinishTime));
        Integer rate = null;
        if (!dueThisMonth.isEmpty()) {
            long onTime = dueThisMonth.stream().filter(t -> CheckRules.DONE.equals(t.getStatus()) && t.getFinishTime() != null
                    && !t.getFinishTime().toLocalDate().isAfter(t.getDueDate())).count();
            rate = (int) Math.round(onTime * 100.0 / dueThisMonth.size());
        }
        return new CheckVOs.Overview(pending, overdue, monthDone.size(),
                monthDone.stream().mapToInt(t -> nz(t.getFailCount())).sum(),
                monthDone.stream().mapToInt(t -> nz(t.getHazardCount())).sum(), rate);
    }

    /** 手机首页：我待检查的任务数、其中逾期的 */
    public int[] myPending() {
        LocalDate today = LocalDate.now();
        return new int[]{count(stateFilter(scope(true), CheckRules.PENDING, today)), count(stateFilter(scope(true), CheckRules.OVERDUE, today))};
    }

    public CheckVOs.TaskDetail detail(Long id) {
        CheckTask t = loadTask(id);
        Long me = StpUtil.getLoginIdAsLong();
        if (!me.equals(t.getCheckerId()) && !StpUtil.hasPermission(PERM_EDIT)) {
            // 没有权限时也返回“不存在”，不暴露任务是否存在
            throw new BizException(404, "检查任务不存在");
        }
        t.setState(CheckRules.state(t.getStatus(), t.getDueDate(), LocalDate.now()));
        List<Map<String, Object>> items;
        if (CheckRules.DONE.equals(t.getStatus())) {
            items = read(t.getResults(), RESULTS);
        } else {
            items = new ArrayList<>();
            for (CheckItem it : read(t.getItems(), ITEMS)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("content", it.content());
                m.put("standard", it.standard());
                m.put("category", it.category());
                items.add(m);
            }
        }
        t.setItems(null);
        t.setResults(null);
        return new CheckVOs.TaskDetail(t, items, CheckRules.PENDING.equals(t.getStatus()) && me.equals(t.getCheckerId()));
    }

    /**
     * 提交检查结果。只有检查人本人能提交；逾期了也可以补检（记录完成时间，统计里算不按时）。
     * 不合格项勾选“转隐患”的，每项生成一条隐患（和隐患页面上报完全一样：编号、记录、通知安全员）
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckVOs.SubmitResult submit(Long id, TaskSubmitDTO dto) {
        CheckTask t = loadTask(id);
        SysUser me = userService.getById(StpUtil.getLoginIdAsLong());
        if (!me.getId().equals(t.getCheckerId())) {
            throw new BizException(403, "只有检查人【" + t.getCheckerName() + "】可以提交这次检查");
        }
        if (!CheckRules.PENDING.equals(t.getStatus())) {
            throw new BizException("这次检查已经提交过了");
        }
        if (t.getPeriodStart().isAfter(LocalDate.now())) {
            throw new BizException("这次检查从 " + t.getPeriodStart() + " 开始，还没到时间");
        }
        List<CheckItem> items = read(t.getItems(), ITEMS);
        List<TaskSubmitDTO.Item> in = dto.getItems();
        String error = CheckRules.checkResults(items.size(),
                in.stream().map(TaskSubmitDTO.Item::getOk).toList(),
                in.stream().map(TaskSubmitDTO.Item::getToHazard).toList(),
                in.stream().map(TaskSubmitDTO.Item::getNote).toList());
        if (error != null) {
            throw new BizException(error);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        List<String> hazardNos = new ArrayList<>();
        int fail = 0;
        for (int i = 0; i < items.size(); i++) {
            CheckItem it = items.get(i);
            TaskSubmitDTO.Item r = in.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", it.content());
            m.put("standard", it.standard());
            m.put("category", it.category());
            m.put("ok", r.getOk());
            m.put("note", trim(r.getNote()));
            if (!r.getOk()) {
                fail++;
                if (Boolean.TRUE.equals(r.getToHazard())) {
                    HazardReportDTO h = new HazardReportDTO();
                    String title = "安全检查发现：" + it.content();
                    h.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
                    String desc = t.getPlanName() + "（" + t.getTaskNo() + "，检查表《" + t.getTemplateName() + "》）第 " + (i + 1)
                            + " 项不合格：" + it.content()
                            + (StringUtils.hasText(it.standard()) ? "\n检查标准：" + it.standard() : "")
                            + (StringUtils.hasText(r.getNote()) ? "\n现场情况：" + r.getNote().trim() : "")
                            + "\n检查人：" + me.getNickname();
                    h.setDescription(desc.length() > 1000 ? desc.substring(0, 1000) : desc);
                    h.setCategory(it.category() != null ? it.category() : "安全管理");
                    h.setLevel(StringUtils.hasText(r.getLevel()) ? r.getLevel() : "GENERAL");
                    h.setLocation(t.getLocation());
                    Long hazardId = hazardService.report(h);
                    Hazard created = hazardMapper.selectById(hazardId);
                    m.put("hazardId", String.valueOf(hazardId));
                    m.put("hazardNo", created.getHazardNo());
                    hazardNos.add(created.getHazardNo());
                }
            }
            results.add(m);
        }
        t.setResults(write(results));
        t.setFailCount(fail);
        t.setHazardCount(hazardNos.size());
        t.setSummary(trim(dto.getSummary()));
        t.setFinishTime(LocalDateTime.now());
        t.setStatus(CheckRules.DONE);
        // 乐观锁：同一张任务在两个页面（手机、电脑）同时提交，只有一个成功
        if (taskMapper.updateById(t) == 0) {
            throw new BizException("这次检查已经被提交过了，请刷新");
        }
        return new CheckVOs.SubmitResult(fail, hazardNos);
    }

    // ============================== 内部方法 ==============================

    public static String link(Long taskId) {
        return "/safety/check?detail=" + taskId;
    }

    /** 安全员看全部；其他人、或者明确要“只看我的”，只看分给自己的 */
    private static LambdaQueryWrapper<CheckTask> scope(Boolean mine) {
        boolean all = StpUtil.hasPermission(PERM_EDIT) && !Boolean.TRUE.equals(mine);
        return Wrappers.<CheckTask>lambdaQuery().eq(!all, CheckTask::getCheckerId, StpUtil.getLoginIdAsLong());
    }

    /** PENDING = 待检查（含逾期，只算已经开始的周期）；OVERDUE = 逾期；DONE = 已完成 */
    static LambdaQueryWrapper<CheckTask> stateFilter(LambdaQueryWrapper<CheckTask> w, String state, LocalDate today) {
        if (!StringUtils.hasText(state)) {
            return w;
        }
        switch (state) {
            case CheckRules.PENDING -> w.eq(CheckTask::getStatus, CheckRules.PENDING).le(CheckTask::getPeriodStart, today);
            case CheckRules.OVERDUE -> w.eq(CheckTask::getStatus, CheckRules.PENDING).lt(CheckTask::getDueDate, today);
            case CheckRules.DONE -> w.eq(CheckTask::getStatus, CheckRules.DONE);
            default -> throw new BizException("任务状态不正确");
        }
        return w;
    }

    private int count(LambdaQueryWrapper<CheckTask> w) {
        return Math.toIntExact(taskMapper.selectCount(w));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    public List<CheckItem> items(String text) {
        return read(text, ITEMS);
    }

    private <T> T read(String text, TypeReference<T> type) {
        try {
            return json.readValue(text == null ? "[]" : text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("检查项数据格式不正确", e);
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private CheckTemplate loadTemplate(Long id) {
        CheckTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "检查表不存在");
        }
        return t;
    }

    private CheckPlan loadPlan(Long id) {
        CheckPlan p = planMapper.selectById(id);
        if (p == null) {
            throw new BizException(404, "检查计划不存在");
        }
        return p;
    }

    public CheckTask loadTask(Long id) {
        CheckTask t = taskMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "检查任务不存在");
        }
        return t;
    }
}
