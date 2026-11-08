package com.safework.ticket.jsa;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.Jsa;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketType;
import com.safework.ticket.mapper.JsaMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 作业安全分析（JSA）库：编写（可以让 AI 起草）→ 提交 → 审核发布（不能审核自己写的）→ 开作业票时引用。
 * 发布满 1 年提示复审：复制一份修改后重新审核，旧的停用。
 */
@Service
@RequiredArgsConstructor
public class JsaService {

    public static final String PERM_LIST = "jsa:list";
    public static final String PERM_EDIT = "jsa:edit";
    public static final String PERM_REVIEW = "jsa:review";
    public static final String BIZ_TYPE = "JSA";
    public static final String LINK = "/ticket/jsa";

    private final JsaMapper jsaMapper;
    private final WorkTicketMapper ticketMapper;
    private final SerialNoGenerator serialNo;
    private final UserService userService;
    private final MessageService messageService;
    private final ObjectMapper json = new ObjectMapper();

    // ============================== 查询 ==============================

    public JsaVOs.Page page(JsaDTOs.Query q) {
        String kw = q.getKeyword();
        var page = jsaMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(q.getCurrent(), q.getSize()),
                Wrappers.<Jsa>lambdaQuery()
                        .select(Jsa.class, f -> !"steps".equals(f.getColumn()))
                        .eq(StringUtils.hasText(q.getStatus()), Jsa::getStatus, q.getStatus())
                        .eq(StringUtils.hasText(q.getTicketType()), Jsa::getTicketType, q.getTicketType())
                        .and(StringUtils.hasText(kw), w -> w.like(Jsa::getTitle, kw).or().like(Jsa::getJsaNo, kw).or().like(Jsa::getWorkContent, kw))
                        .last("ORDER BY CASE status WHEN 'REVIEWING' THEN 0 WHEN 'REJECTED' THEN 1 WHEN 'DRAFT' THEN 2 WHEN 'PUBLISHED' THEN 3 ELSE 4 END, update_time DESC"));
        Map<Long, Long> counts = ticketCounts(page.getRecords().stream().map(Jsa::getId).toList());
        LocalDate today = LocalDate.now();
        return new JsaVOs.Page(page.getRecords().stream().map(j -> new JsaVOs.Row(j, JsaRules.level(n(j.getMaxRisk())),
                counts.getOrDefault(j.getId(), 0L), JsaRules.reviewDue(j.getStatus(), j.getPublishTime(), today))).toList(), page.getTotal());
    }

    public JsaVOs.Detail detail(Long id) {
        Jsa j = load(id);
        List<JsaVOs.TicketRef> tickets = ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery().eq(WorkTicket::getJsaId, id)
                        .orderByDesc(WorkTicket::getPlanStartTime).last("LIMIT 20"))
                .stream().map(t -> new JsaVOs.TicketRef(t.getId(), t.getTicketNo(), t.getTitle(), t.getStatus().name(), t.getPlanStartTime())).toList();
        Long me = StpUtil.getLoginIdAsLong();
        boolean author = me.equals(j.getAuthorId());
        List<String> actions = new ArrayList<>();
        if (StpUtil.hasPermission(PERM_EDIT)) {
            if (JsaRules.editable(j.getStatus()) && author) {
                actions.addAll(List.of("EDIT", "SUBMIT", "DELETE"));
            }
            actions.add("COPY");
        }
        if (StpUtil.hasPermission(PERM_REVIEW)) {
            if (JsaRules.REVIEWING.equals(j.getStatus()) && !author) {
                actions.addAll(List.of("APPROVE", "REJECT"));
            }
            if (JsaRules.PUBLISHED.equals(j.getStatus())) {
                actions.add("ARCHIVE");
            }
        }
        return new JsaVOs.Detail(j, views(parse(j.getSteps())), JsaRules.level(n(j.getMaxRisk())),
                JsaRules.reviewDue(j.getStatus(), j.getPublishTime(), LocalDate.now()), tickets, actions);
    }

    /** 开作业票时可以引用的：已发布，作业类型一致或通用 */
    public List<JsaVOs.Option> usable(String ticketType) {
        return jsaMapper.selectList(Wrappers.<Jsa>lambdaQuery().eq(Jsa::getStatus, JsaRules.PUBLISHED)
                        .and(StringUtils.hasText(ticketType), w -> w.eq(Jsa::getTicketType, ticketType).or().isNull(Jsa::getTicketType))
                        .orderByAsc(Jsa::getTicketType).orderByDesc(Jsa::getPublishTime))
                .stream().map(this::option).toList();
    }

    /** 智能体检索：已发布的，按关键词匹配名称、作业内容、步骤 */
    public List<JsaVOs.Option> search(String keyword, String ticketType, int limit) {
        return jsaMapper.selectList(Wrappers.<Jsa>lambdaQuery().eq(Jsa::getStatus, JsaRules.PUBLISHED)
                        .eq(StringUtils.hasText(ticketType), Jsa::getTicketType, ticketType)
                        .and(StringUtils.hasText(keyword), w -> w.like(Jsa::getTitle, keyword).or().like(Jsa::getWorkContent, keyword)
                                .or().like(Jsa::getSteps, keyword).or().like(Jsa::getLocation, keyword))
                        .orderByDesc(Jsa::getPublishTime).last("LIMIT " + Math.max(1, Math.min(limit, 10))))
                .stream().map(this::option).toList();
    }

    /** 概况：不依赖登录（大屏用） */
    public JsaVOs.Overview overview() {
        List<Jsa> all = jsaMapper.selectList(Wrappers.<Jsa>lambdaQuery().select(Jsa::getId, Jsa::getStatus, Jsa::getPublishTime));
        LocalDate today = LocalDate.now();
        return new JsaVOs.Overview(count(all, JsaRules.PUBLISHED), count(all, JsaRules.REVIEWING),
                (int) all.stream().filter(j -> JsaRules.reviewDue(j.getStatus(), j.getPublishTime(), today)).count(),
                count(all, JsaRules.DRAFT) + count(all, JsaRules.REJECTED));
    }

    /** 作业票引用前检查（TicketService 用） */
    public Jsa checkUsable(Long id, TicketType ticketType) {
        Jsa j = jsaMapper.selectById(id);
        if (j == null) {
            throw new BizException("引用的 JSA 不存在");
        }
        String err = JsaRules.checkUsable(j.getStatus(), j.getTicketType(), ticketType.name());
        if (err != null) {
            throw new BizException(err + "（" + j.getJsaNo() + "）");
        }
        return j;
    }

    // ============================== 编写 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long id, JsaDTOs.Save dto) {
        String type = StringUtils.hasText(dto.getTicketType()) ? dto.getTicketType() : null;
        if (type != null && java.util.Arrays.stream(TicketType.values()).noneMatch(t -> t.name().equals(type))) {
            throw new BizException("作业类型不正确");
        }
        List<JsaRules.Step> steps = dto.getSteps().stream().map(s -> new JsaRules.Step(trim(s.getStep()), trim(s.getHazards()),
                s.getLikelihood(), s.getSeverity(), trim(s.getControls()))).toList();
        String err = JsaRules.checkSteps(steps);
        if (err != null) {
            throw new BizException(err);
        }
        SysUser me = me();
        Jsa j;
        if (id == null) {
            j = new Jsa();
            j.setStatus(JsaRules.DRAFT);
            j.setAuthorId(me.getId());
            j.setAuthorName(me.getNickname());
            j.setAiDrafted(0);
        } else {
            j = load(id);
            if (!me.getId().equals(j.getAuthorId())) {
                throw new BizException(403, "只有编写人可以修改");
            }
            if (!JsaRules.editable(j.getStatus())) {
                throw new BizException("只有草稿和被驳回的可以修改；已发布的请复制一份修改");
            }
        }
        j.setTitle(dto.getTitle().trim());
        j.setTicketType(type);
        j.setWorkContent(dto.getWorkContent().trim());
        j.setLocation(trim(dto.getLocation()));
        j.setPpe(trim(dto.getPpe()));
        j.setSteps(write(steps));
        j.setStepCount(steps.size());
        j.setMaxRisk(JsaRules.maxRisk(steps));
        if (Boolean.TRUE.equals(dto.getAiDrafted())) {
            j.setAiDrafted(1);
        }
        if (id == null) {
            j.setJsaNo(serialNo.next("JSA"));
            jsaMapper.insert(j);
        } else {
            jsaMapper.updateById(j);
        }
        return j.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        Jsa j = load(id);
        if (!Long.valueOf(StpUtil.getLoginIdAsLong()).equals(j.getAuthorId())) {
            throw new BizException(403, "只有编写人可以提交");
        }
        fire(j, JsaRules.Action.SUBMIT);
        j.setReviewComment(null);
        jsaMapper.updateById(j);
        messageService.sendToPermission(PERM_REVIEW, j.getAuthorId(), MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, j.getId(),
                "JSA 待审核：" + j.getTitle(), j.getAuthorName() + " 提交了作业安全分析 " + j.getJsaNo() + "（" + j.getStepCount() + " 个步骤，最高风险 "
                        + JsaRules.LEVELS.get(JsaRules.level(n(j.getMaxRisk()))) + "）", LINK + "?detail=" + j.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Jsa j = load(id);
        SysUser me = me();
        if (me.getId().equals(j.getAuthorId())) {
            throw new BizException("不能审核自己编写的 JSA");
        }
        fire(j, JsaRules.Action.APPROVE);
        j.setReviewerId(me.getId());
        j.setReviewerName(me.getNickname());
        j.setReviewTime(LocalDateTime.now());
        j.setPublishTime(LocalDateTime.now());
        jsaMapper.updateById(j);
        // 复审：新的发布后，复制来源自动停用
        if (j.getCopiedFrom() != null) {
            Jsa old = jsaMapper.selectById(j.getCopiedFrom());
            if (old != null && JsaRules.PUBLISHED.equals(old.getStatus())) {
                old.setStatus(JsaRules.ARCHIVED);
                jsaMapper.updateById(old);
            }
        }
        notifyAuthor(j, "JSA 已发布：" + j.getTitle(), me.getNickname() + " 审核通过，开作业票时可以引用了");
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String comment) {
        Jsa j = load(id);
        SysUser me = me();
        if (me.getId().equals(j.getAuthorId())) {
            throw new BizException("不能审核自己编写的 JSA");
        }
        fire(j, JsaRules.Action.REJECT);
        j.setReviewerId(me.getId());
        j.setReviewerName(me.getNickname());
        j.setReviewTime(LocalDateTime.now());
        j.setReviewComment(comment.trim());
        jsaMapper.updateById(j);
        notifyAuthor(j, "JSA 被驳回：" + j.getTitle(), me.getNickname() + "：" + comment.trim());
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        Jsa j = load(id);
        fire(j, JsaRules.Action.ARCHIVE);
        jsaMapper.updateById(j);
    }

    /** 复制一份草稿（复审或者照着改一个新作业） */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id) {
        Jsa src = load(id);
        SysUser me = me();
        Jsa j = new Jsa();
        j.setJsaNo(serialNo.next("JSA"));
        j.setTitle(src.getTitle().length() > 56 ? src.getTitle().substring(0, 56) : src.getTitle());
        j.setTicketType(src.getTicketType());
        j.setWorkContent(src.getWorkContent());
        j.setLocation(src.getLocation());
        j.setPpe(src.getPpe());
        j.setSteps(src.getSteps());
        j.setStepCount(src.getStepCount());
        j.setMaxRisk(src.getMaxRisk());
        j.setAiDrafted(src.getAiDrafted());
        j.setStatus(JsaRules.DRAFT);
        j.setAuthorId(me.getId());
        j.setAuthorName(me.getNickname());
        j.setCopiedFrom(JsaRules.PUBLISHED.equals(src.getStatus()) ? src.getId() : null);
        jsaMapper.insert(j);
        return j.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Jsa j = load(id);
        if (!Long.valueOf(StpUtil.getLoginIdAsLong()).equals(j.getAuthorId()) || !JsaRules.editable(j.getStatus())) {
            throw new BizException("只有编写人可以删除自己的草稿或被驳回的 JSA");
        }
        jsaMapper.deleteById(id);
    }

    // ============================== 内部 ==============================

    private JsaVOs.Option option(Jsa j) {
        return new JsaVOs.Option(j.getId(), j.getJsaNo(), j.getTitle(), j.getTicketType(), JsaRules.level(n(j.getMaxRisk())), j.getPpe(), views(parse(j.getSteps())));
    }

    static List<JsaVOs.StepView> views(List<JsaRules.Step> steps) {
        List<JsaVOs.StepView> l = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            JsaRules.Step s = steps.get(i);
            int r = JsaRules.risk(s);
            l.add(new JsaVOs.StepView(i + 1, s.step(), s.hazards(), s.likelihood(), s.severity(), r, JsaRules.level(r), s.controls()));
        }
        return l;
    }

    private Map<Long, Long> ticketCounts(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery().select(WorkTicket::getId, WorkTicket::getJsaId).in(WorkTicket::getJsaId, ids))
                .stream().collect(Collectors.groupingBy(WorkTicket::getJsaId, Collectors.counting()));
    }

    private void notifyAuthor(Jsa j, String title, String content) {
        messageService.send(List.of(j.getAuthorId()), MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE, j.getId(), title, content, LINK + "?detail=" + j.getId()));
    }

    private static void fire(Jsa j, JsaRules.Action a) {
        if (!a.canFire(j.getStatus())) {
            throw new BizException("当前状态【" + JsaRules.STATUS.get(j.getStatus()) + "】不能" + a.label());
        }
        j.setStatus(a.to());
    }

    private List<JsaRules.Step> parse(String steps) {
        if (!StringUtils.hasText(steps)) {
            return List.of();
        }
        try {
            return json.readValue(steps, new TypeReference<List<JsaRules.Step>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String write(List<JsaRules.Step> steps) {
        try {
            return json.writeValueAsString(steps);
        } catch (Exception e) {
            throw new BizException("作业步骤保存失败");
        }
    }

    private Jsa load(Long id) {
        Jsa j = jsaMapper.selectById(id);
        if (j == null) {
            throw new BizException(404, "JSA 不存在");
        }
        return j;
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private static int count(List<Jsa> l, String status) {
        return (int) l.stream().filter(j -> status.equals(j.getStatus())).count();
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
