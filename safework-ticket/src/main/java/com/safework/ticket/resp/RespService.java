package com.safework.ticket.resp;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.safety.check.CheckRules;
import com.safework.safety.entity.CheckTask;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.PpePost;
import com.safework.safety.entity.PpePostUser;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.CheckTaskMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.PpePostMapper;
import com.safework.safety.mapper.PpePostUserMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.RespBook;
import com.safework.ticket.entity.RespDuty;
import com.safework.ticket.entity.RespSign;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.mapper.RespBookMapper;
import com.safework.ticket.mapper.RespDutyMapper;
import com.safework.ticket.mapper.RespSignMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import com.safework.ticket.sign.SignatureImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 安全生产责任制：岗位安全职责 → 年度责任书（通用条款 + 各人岗位职责）→ 本人阅读并手写签署 → 年度考核。
 * <pre>
 * 岗位（劳保模块）+ 岗位职责 ──┐
 *                              ├─▶ 发布年度责任书：给岗位上的每个人生成一份（职责快照）──本人手写签名──▶ 已签署
 * 通用条款、签署截止日期 ───────┘           │ 新来的人：“同步人员”补发         └─ 年底考核打分（参考隐患、检查、作业票数据）
 * </pre>
 * 有 resp:edit 的管理员维护职责、发布责任书、考核；其他人只能看、签自己的。
 */
@Service
@RequiredArgsConstructor
public class RespService {

    public static final String PERM_LIST = "resp:list";
    public static final String PERM_EDIT = "resp:edit";
    public static final String LINK = "/safety/resp";

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final RespDutyMapper dutyMapper;
    private final RespBookMapper bookMapper;
    private final RespSignMapper signMapper;
    private final PpePostMapper postMapper;
    private final PpePostUserMapper postUserMapper;
    private final HazardMapper hazardMapper;
    private final CheckTaskMapper checkTaskMapper;
    private final WorkTicketMapper ticketMapper;
    private final UserService userService;
    private final MessageService messageService;
    private final ObjectMapper json = new ObjectMapper();

    // ============================== 岗位职责 ==============================

    public List<RespVOs.PostDuty> postDuties() {
        List<PpePost> posts = postMapper.selectList(Wrappers.<PpePost>lambdaQuery()
                .orderByDesc(PpePost::getStatus).orderByAsc(PpePost::getName));
        Map<Long, Long> members = postUserMapper.selectList(Wrappers.<PpePostUser>lambdaQuery())
                .stream().collect(Collectors.groupingBy(PpePostUser::getPostId, Collectors.counting()));
        Map<Long, RespDuty> duties = dutyMapper.selectList(Wrappers.<RespDuty>lambdaQuery())
                .stream().collect(Collectors.toMap(RespDuty::getPostId, Function.identity(), (a, b) -> a));
        return posts.stream().map(p -> {
            RespDuty d = duties.get(p.getId());
            return new RespVOs.PostDuty(p.getId(), p.getName(), p.getStatus(), members.getOrDefault(p.getId(), 0L).intValue(),
                    d == null ? List.of() : read(d.getDuties()), d == null ? null : d.getUpdaterName());
        }).toList();
    }

    /** 保存岗位职责。只影响以后发布的责任书，已经发布的保存的是当时的快照 */
    @Transactional(rollbackFor = Exception.class)
    public void saveDuties(Long postId, List<String> duties) {
        if (postMapper.selectById(postId) == null) {
            throw new BizException(404, "岗位不存在");
        }
        String error = RespRules.checkDuties(duties);
        if (error != null) {
            throw new BizException(error);
        }
        RespDuty d = dutyMapper.selectOne(Wrappers.<RespDuty>lambdaQuery().eq(RespDuty::getPostId, postId));
        boolean create = d == null;
        if (create) {
            d = new RespDuty();
            d.setPostId(postId);
        }
        d.setDuties(write(RespRules.clean(duties)));
        d.setUpdaterName(me().getNickname());
        if (create) {
            dutyMapper.insert(d);
        } else {
            dutyMapper.updateById(d);
        }
    }

    // ============================== 年度责任书 ==============================

    public List<RespVOs.Book> books() {
        List<RespBook> books = bookMapper.selectList(Wrappers.<RespBook>lambdaQuery()
                .orderByDesc(RespBook::getYear).orderByDesc(RespBook::getCreateTime));
        if (books.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RespSign>> signs = signMapper.selectList(Wrappers.<RespSign>lambdaQuery()
                        .in(RespSign::getBookId, books.stream().map(RespBook::getId).toList()))
                .stream().collect(Collectors.groupingBy(RespSign::getBookId));
        return books.stream().map(b -> {
            List<RespSign> l = signs.getOrDefault(b.getId(), List.of());
            return new RespVOs.Book(b, l.size(),
                    (int) l.stream().filter(s -> RespRules.SIGNED.equals(s.getStatus())).count(),
                    (int) l.stream().filter(s -> RespRules.late(s.getSignTime(), b.getDeadline())).count(),
                    (int) l.stream().filter(s -> s.getScore() != null).count());
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveBook(Long id, RespDTOs.BookSave dto) {
        LocalDate today = LocalDate.now();
        String error = RespRules.checkYear(dto.getYear(), today);
        if (error != null) {
            throw new BizException(error);
        }
        if (dto.getDeadline().isBefore(today)) {
            throw new BizException("签署截止日期不能早于今天");
        }
        RespBook b = id == null ? new RespBook() : loadBook(id);
        if (id != null && !RespRules.DRAFT.equals(b.getStatus())) {
            throw new BizException("已经发布的责任书不能修改（大家签的是发布时的内容）");
        }
        b.setYear(dto.getYear());
        b.setTitle(dto.getTitle().trim());
        b.setContent(dto.getContent().trim());
        b.setDeadline(dto.getDeadline());
        b.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null);
        if (id == null) {
            b.setStatus(RespRules.DRAFT);
            bookMapper.insert(b);
        } else {
            bookMapper.updateById(b);
        }
        return b.getId();
    }

    /** 发布：给配置了职责的岗位上的每个人生成一份待签署的责任书，并通知本人 */
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long id) {
        RespBook b = loadBook(id);
        if (!RespRules.DRAFT.equals(b.getStatus())) {
            throw new BizException("只有草稿可以发布");
        }
        if (b.getDeadline().isBefore(LocalDate.now())) {
            throw new BizException("签署截止日期已经过了，请先修改");
        }
        b.setStatus(RespRules.PUBLISHED);
        b.setPublishTime(LocalDateTime.now());
        b.setPublisherName(me().getNickname());
        bookMapper.updateById(b);
        int n = addMissing(b);
        if (n == 0) {
            throw new BizException("没有需要签署的人：请先在【岗位职责】里给岗位填写职责，并在【劳保用品 → 岗位标准】里给岗位安排人员");
        }
        return n;
    }

    /** 发布后岗位上新来的人：补发责任书（已经有的不重复） */
    @Transactional(rollbackFor = Exception.class)
    public int syncMembers(Long id) {
        RespBook b = loadBook(id);
        if (!RespRules.PUBLISHED.equals(b.getStatus())) {
            throw new BizException("只有签署中的责任书可以同步人员");
        }
        return addMissing(b);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        RespBook b = loadBook(id);
        if (!RespRules.PUBLISHED.equals(b.getStatus())) {
            throw new BizException("只有签署中的责任书可以结束");
        }
        b.setStatus(RespRules.CLOSED);
        bookMapper.updateById(b);
    }

    /** 草稿可以删除；发布后有签署记录，不能删 */
    @Transactional(rollbackFor = Exception.class)
    public void removeDraft(Long id) {
        RespBook b = loadBook(id);
        if (!RespRules.DRAFT.equals(b.getStatus())) {
            throw new BizException("只有草稿可以删除");
        }
        bookMapper.deleteById(id);
    }

    /**
     * 给还没有责任书的人生成一份：职责 = 他所在的各个岗位的职责合并去重（快照）。
     * 唯一索引 (book_id, user_id) 保证同一个人只有一份。
     */
    private int addMissing(RespBook b) {
        Map<Long, PpePost> posts = postMapper.selectList(Wrappers.<PpePost>lambdaQuery().eq(PpePost::getStatus, 1))
                .stream().collect(Collectors.toMap(PpePost::getId, Function.identity()));
        Map<Long, List<String>> duties = new LinkedHashMap<>();
        dutyMapper.selectList(Wrappers.<RespDuty>lambdaQuery()).forEach(d -> {
            if (posts.containsKey(d.getPostId())) {
                duties.put(d.getPostId(), read(d.getDuties()));
            }
        });
        if (duties.isEmpty()) {
            return 0;
        }
        Set<Long> existing = signMapper.selectList(Wrappers.<RespSign>lambdaQuery().eq(RespSign::getBookId, b.getId())
                        .select(RespSign::getUserId))
                .stream().map(RespSign::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, List<Long>> userPosts = new LinkedHashMap<>();
        postUserMapper.selectList(Wrappers.<PpePostUser>lambdaQuery().in(PpePostUser::getPostId, duties.keySet())).forEach(m -> {
            names.put(m.getUserId(), m.getUserName());
            userPosts.computeIfAbsent(m.getUserId(), k -> new ArrayList<>()).add(m.getPostId());
        });
        List<Long> added = new ArrayList<>();
        userPosts.forEach((uid, pids) -> {
            if (existing.contains(uid)) {
                return;
            }
            RespSign s = new RespSign();
            s.setBookId(b.getId());
            s.setUserId(uid);
            s.setUserName(names.get(uid));
            s.setPosts(pids.stream().map(pid -> posts.get(pid).getName()).distinct().collect(Collectors.joining("、")));
            s.setDuties(write(RespRules.merge(pids.stream().map(duties::get).toList())));
            s.setStatus(RespRules.PENDING);
            signMapper.insert(s);
            added.add(uid);
        });
        if (!added.isEmpty()) {
            messageService.send(added, MessageDraft.of(MessageCategory.TODO, "RESP", null, "请签署《" + b.getTitle() + "》",
                    "请阅读你的岗位安全职责并手写签名，截止 " + b.getDeadline(), LINK));
        }
        return added.size();
    }

    // ============================== 签署 ==============================

    /** 某份责任书的签署情况（管理员） */
    public RespVOs.SignPage signs(Long bookId, RespDTOs.SignQuery q) {
        RespBook b = loadBook(bookId);
        String kw = q.getKeyword();
        LambdaQueryWrapper<RespSign> w = Wrappers.<RespSign>lambdaQuery()
                .eq(RespSign::getBookId, bookId)
                .eq(StringUtils.hasText(q.getStatus()), RespSign::getStatus, q.getStatus())
                .isNull(Boolean.TRUE.equals(q.getUnassessed()), RespSign::getScore)
                .and(StringUtils.hasText(kw), x -> x.like(RespSign::getUserName, kw).or().like(RespSign::getPosts, kw))
                .orderByAsc(RespSign::getStatus)
                .orderByAsc(RespSign::getUserName);
        Page<RespSign> page = signMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), w);
        page.getRecords().forEach(s -> {
            s.setLate(RespRules.late(s.getSignTime(), b.getDeadline()));
            s.setDuties(null);
        });
        return new RespVOs.SignPage(page.getRecords(), page.getTotal());
    }

    /** 我的责任书（所有年度） */
    public List<RespVOs.Book> mine() {
        Long me = StpUtil.getLoginIdAsLong();
        List<RespSign> signs = signMapper.selectList(Wrappers.<RespSign>lambdaQuery().eq(RespSign::getUserId, me));
        if (signs.isEmpty()) {
            return List.of();
        }
        Map<Long, RespBook> books = bookMapper.selectBatchIds(signs.stream().map(RespSign::getBookId).toList())
                .stream().collect(Collectors.toMap(RespBook::getId, Function.identity()));
        // 复用 Book 结构：total = 1；signed = 是否已签；assessed = 是否已考核
        return signs.stream().filter(s -> books.containsKey(s.getBookId()))
                .sorted((a, c) -> books.get(c.getBookId()).getYear() - books.get(a.getBookId()).getYear())
                .map(s -> new RespVOs.Book(books.get(s.getBookId()), 1, RespRules.SIGNED.equals(s.getStatus()) ? 1 : 0,
                        RespRules.late(s.getSignTime(), books.get(s.getBookId()).getDeadline()) ? 1 : 0, s.getScore() == null ? 0 : 1))
                .toList();
    }

    /** 一份责任书的完整内容：本人或管理员能看；签名图片也只在这里返回 */
    public RespVOs.SignDetail detail(Long signId) {
        RespSign s = loadSign(signId);
        Long me = StpUtil.getLoginIdAsLong();
        if (!me.equals(s.getUserId()) && !StpUtil.hasPermission(PERM_EDIT)) {
            throw new BizException(404, "责任书不存在");
        }
        RespBook b = loadBook(s.getBookId());
        s.setLate(RespRules.late(s.getSignTime(), b.getDeadline()));
        String image = RespRules.SIGNED.equals(s.getStatus())
                ? signMapper.selectOne(Wrappers.<RespSign>lambdaQuery().eq(RespSign::getId, signId).select(RespSign::getSignImage)).getSignImage()
                : null;
        List<String> duties = read(s.getDuties());
        s.setDuties(null);
        boolean canSign = me.equals(s.getUserId()) && RespRules.PENDING.equals(s.getStatus()) && RespRules.PUBLISHED.equals(b.getStatus());
        // 本人看自己的考核分数，管理员都能看；分数在考核完成后才显示给本人
        return new RespVOs.SignDetail(b, s, duties, image, canSign);
    }

    /** 我在某份责任书里的记录（通过责任书 ID 找），手机端从“我的责任书”列表进入 */
    public Long mySignId(Long bookId) {
        RespSign s = signMapper.selectOne(Wrappers.<RespSign>lambdaQuery()
                .eq(RespSign::getBookId, bookId).eq(RespSign::getUserId, StpUtil.getLoginIdAsLong()));
        if (s == null) {
            throw new BizException(404, "你没有这份责任书");
        }
        return s.getId();
    }

    /** 本人手写签署：签名校验和作业票一样（PNG、大小、文件头），保存 SHA-256；乐观锁防止重复提交 */
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long signId, String signature) {
        RespSign s = loadSign(signId);
        if (!s.getUserId().equals(StpUtil.getLoginIdAsLong())) {
            throw new BizException(403, "只能本人签署");
        }
        if (!RespRules.PENDING.equals(s.getStatus())) {
            throw new BizException("已经签署过了");
        }
        RespBook b = loadBook(s.getBookId());
        if (!RespRules.PUBLISHED.equals(b.getStatus())) {
            throw new BizException("这份责任书已经结束签署");
        }
        SignatureImage.Checked checked;
        try {
            checked = SignatureImage.check(signature);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        s.setSignImage(checked.dataUrl());
        s.setSignHash(checked.sha256());
        s.setSignTime(LocalDateTime.now());
        s.setStatus(RespRules.SIGNED);
        if (signMapper.updateById(s) == 0) {
            throw new BizException("已经签署过了，请刷新");
        }
    }

    // ============================== 年度考核 ==============================

    /** 考核参考数据：这个人在责任书年度里的隐患、整改、检查、作业票表现 */
    public RespVOs.Reference reference(Long signId) {
        RespSign s = loadSign(signId);
        RespBook b = loadBook(s.getBookId());
        Long uid = s.getUserId();
        LocalDateTime from = LocalDate.of(b.getYear(), 1, 1).atStartOfDay();
        LocalDateTime to = from.plusYears(1);
        LocalDate today = LocalDate.now();

        int reported = Math.toIntExact(hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getReporterId, uid).ge(Hazard::getCreateTime, from).lt(Hazard::getCreateTime, to)));
        List<Hazard> rectify = hazardMapper.selectList(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getRectifierId, uid).ne(Hazard::getStatus, HazardStatus.REVOKED)
                .ge(Hazard::getDeadline, from).lt(Hazard::getDeadline, to)
                .le(Hazard::getDeadline, LocalDateTime.now())
                .select(Hazard::getDeadline, Hazard::getRectifyTime));
        long rectifyOnTime = rectify.stream().filter(h -> h.getRectifyTime() != null && !h.getRectifyTime().isAfter(h.getDeadline())).count();
        List<CheckTask> checks = checkTaskMapper.selectList(Wrappers.<CheckTask>lambdaQuery()
                .eq(CheckTask::getCheckerId, uid)
                .ge(CheckTask::getDueDate, from.toLocalDate()).lt(CheckTask::getDueDate, to.toLocalDate())
                .lt(CheckTask::getDueDate, today)
                .select(CheckTask::getStatus, CheckTask::getDueDate, CheckTask::getFinishTime));
        long checkOnTime = checks.stream().filter(k -> CheckRules.DONE.equals(k.getStatus()) && k.getFinishTime() != null
                && !k.getFinishTime().toLocalDate().isAfter(k.getDueDate())).count();
        int tickets = Math.toIntExact(ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery()
                .eq(WorkTicket::getStatus, TicketStatus.COMPLETED)
                .ge(WorkTicket::getActualEndTime, from).lt(WorkTicket::getActualEndTime, to)
                .and(w -> w.eq(WorkTicket::getApplicantId, uid).or().eq(WorkTicket::getGuardianId, uid))));
        return new RespVOs.Reference(reported, rectify.size(), (int) rectifyOnTime, RespRules.percent(rectifyOnTime, rectify.size()),
                checks.size(), (int) checkOnTime, RespRules.percent(checkOnTime, checks.size()), tickets);
    }

    /** 年度考核打分（可以修改），通知本人结果 */
    @Transactional(rollbackFor = Exception.class)
    public String assess(Long signId, int score, String comment) {
        RespSign s = loadSign(signId);
        RespBook b = loadBook(s.getBookId());
        if (RespRules.DRAFT.equals(b.getStatus())) {
            throw new BizException("责任书还没发布");
        }
        String grade = RespRules.grade(score);
        boolean first = s.getScore() == null;
        s.setScore(score);
        s.setGrade(grade);
        s.setAssessComment(StringUtils.hasText(comment) ? comment.trim() : null);
        s.setAssessorName(me().getNickname());
        s.setAssessTime(LocalDateTime.now());
        if (signMapper.updateById(s) == 0) {
            throw new BizException("刚被别人修改过，请刷新");
        }
        if (first) {
            messageService.send(List.of(s.getUserId()), MessageDraft.of(MessageCategory.NOTICE, "RESP", null,
                    b.getYear() + " 年度安全生产责任考核结果：" + grade,
                    "考核得分 " + score + " 分" + (s.getAssessComment() == null ? "" : "。" + s.getAssessComment()), LINK));
        }
        return grade;
    }

    /**
     * 手机端首页：签署中、我还没签的责任书数量。
     * 没有菜单权限、也从来没有责任书的人返回 null（不显示入口）
     */
    public Integer myPending() {
        Long me = StpUtil.getLoginIdAsLong();
        List<RespSign> mine = signMapper.selectList(Wrappers.<RespSign>lambdaQuery()
                .eq(RespSign::getUserId, me).select(RespSign::getBookId, RespSign::getStatus));
        if (mine.isEmpty()) {
            return StpUtil.hasPermission(PERM_LIST) ? 0 : null;
        }
        List<Long> pendingBooks = mine.stream().filter(x -> RespRules.PENDING.equals(x.getStatus())).map(RespSign::getBookId).toList();
        return pendingBooks.isEmpty() ? 0 : Math.toIntExact(bookMapper.selectCount(Wrappers.<RespBook>lambdaQuery()
                .in(RespBook::getId, pendingBooks).eq(RespBook::getStatus, RespRules.PUBLISHED)));
    }

    /** 数据大屏：签署中、已过截止日期还没签的人数 */
    public long overdueUnsigned(LocalDate today) {
        List<Long> books = bookMapper.selectList(Wrappers.<RespBook>lambdaQuery()
                .eq(RespBook::getStatus, RespRules.PUBLISHED).lt(RespBook::getDeadline, today).select(RespBook::getId))
                .stream().map(RespBook::getId).toList();
        return books.isEmpty() ? 0 : signMapper.selectCount(Wrappers.<RespSign>lambdaQuery()
                .in(RespSign::getBookId, books).eq(RespSign::getStatus, RespRules.PENDING));
    }

    // ============================== 内部方法 ==============================

    /** 定时任务用：签署中、还没签的 */
    public List<RespSign> pendingOf(Long bookId) {
        return signMapper.selectList(Wrappers.<RespSign>lambdaQuery()
                .eq(RespSign::getBookId, bookId).eq(RespSign::getStatus, RespRules.PENDING));
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private List<String> read(String text) {
        try {
            return text == null ? List.of() : json.readValue(text, STRINGS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("职责数据格式不正确", e);
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private RespBook loadBook(Long id) {
        RespBook b = bookMapper.selectById(id);
        if (b == null) {
            throw new BizException(404, "责任书不存在");
        }
        return b;
    }

    private RespSign loadSign(Long id) {
        RespSign s = signMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "责任书不存在");
        }
        return s;
    }
}
