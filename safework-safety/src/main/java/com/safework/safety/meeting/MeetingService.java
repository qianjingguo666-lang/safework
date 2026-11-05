package com.safework.safety.meeting;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.MeetingAttendee;
import com.safework.safety.entity.MeetingTeam;
import com.safework.safety.entity.MeetingTeamMember;
import com.safework.safety.entity.ShiftMeeting;
import com.safework.safety.mapper.MeetingAttendeeMapper;
import com.safework.safety.mapper.MeetingTeamMapper;
import com.safework.safety.mapper.MeetingTeamMemberMapper;
import com.safework.safety.mapper.ShiftMeetingMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 班前会 / 班组安全活动：班组 → 班组长开班前会（安全要点、点名、身体精神状态）→ 成员确认 → 开展率统计。
 * 状态不好（身体不适、精神疲劳、饮酒）的人当天不能上岗，通知安全员。
 */
@Service
@RequiredArgsConstructor
public class MeetingService {

    public static final String PERM_LIST = "meeting:list";
    public static final String PERM_EDIT = "meeting:edit";
    public static final String BIZ_TYPE = "MEETING";
    public static final String TAG_PHOTO = "PHOTO";
    public static final String LINK = "/safety/meeting";

    private final MeetingTeamMapper teamMapper;
    private final MeetingTeamMemberMapper memberMapper;
    private final ShiftMeetingMapper meetingMapper;
    private final MeetingAttendeeMapper attendeeMapper;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 班组 ==============================

    public List<MeetingVOs.Team> teams() {
        return buildTeams(teamMapper.selectList(Wrappers.<MeetingTeam>lambdaQuery()
                .orderByDesc(MeetingTeam::getStatus).orderByAsc(MeetingTeam::getName)), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveTeam(Long id, MeetingDTOs.TeamSave dto) {
        SysUser leader = activeUser(dto.getLeaderId());
        Set<Long> ids = new LinkedHashSet<>(dto.getMemberIds());
        ids.add(leader.getId());
        if (ids.size() > 100) {
            throw new BizException("一个班组最多 100 人");
        }
        List<SysUser> users = ids.stream().map(this::activeUser).toList();
        MeetingTeam t = id == null ? new MeetingTeam() : loadTeam(id);
        t.setName(dto.getName().trim());
        t.setLeaderId(leader.getId());
        t.setLeaderName(leader.getNickname());
        t.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null);
        if (id == null) {
            t.setStatus(1);
            teamMapper.insert(t);
        } else {
            teamMapper.updateById(t);
            memberMapper.delete(Wrappers.<MeetingTeamMember>lambdaQuery().eq(MeetingTeamMember::getTeamId, id));
        }
        for (SysUser u : users) {
            MeetingTeamMember m = new MeetingTeamMember();
            m.setTeamId(t.getId());
            m.setUserId(u.getId());
            m.setUserName(u.getNickname());
            memberMapper.insert(m);
        }
        return t.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void teamStatus(Long id, int status) {
        MeetingTeam t = loadTeam(id);
        t.setStatus(status == 1 ? 1 : 0);
        teamMapper.updateById(t);
    }

    // ============================== 班前会 ==============================

    public MeetingVOs.Overview overview() {
        LocalDate today = LocalDate.now();
        List<MeetingTeam> teams = teamMapper.selectList(Wrappers.<MeetingTeam>lambdaQuery().eq(MeetingTeam::getStatus, 1));
        List<ShiftMeeting> recent = meetingMapper.selectList(Wrappers.<ShiftMeeting>lambdaQuery()
                .ge(ShiftMeeting::getMeetingDate, today.minusDays(MeetingRules.STAT_DAYS - 1))
                .select(ShiftMeeting::getId, ShiftMeeting::getTeamId, ShiftMeeting::getMeetingDate, ShiftMeeting::getUnfitCount));
        Set<Long> active = teams.stream().map(MeetingTeam::getId).collect(Collectors.toSet());
        List<ShiftMeeting> todays = recent.stream().filter(m -> today.equals(m.getMeetingDate())).toList();
        long heldDays = recent.stream().filter(m -> active.contains(m.getTeamId()))
                .map(m -> m.getTeamId() + ":" + m.getMeetingDate()).distinct().count();
        int unconfirmed = todays.isEmpty() ? 0 : Math.toIntExact(attendeeMapper.selectCount(Wrappers.<MeetingAttendee>lambdaQuery()
                .in(MeetingAttendee::getMeetingId, todays.stream().map(ShiftMeeting::getId).toList())
                .eq(MeetingAttendee::getAttend, MeetingRules.PRESENT).isNull(MeetingAttendee::getConfirmTime)));
        return new MeetingVOs.Overview(teams.size(),
                (int) todays.stream().filter(m -> active.contains(m.getTeamId())).map(ShiftMeeting::getTeamId).distinct().count(),
                todays.stream().mapToInt(m -> m.getUnfitCount() == null ? 0 : m.getUnfitCount()).sum(),
                MeetingRules.rate(heldDays, (long) teams.size() * MeetingRules.STAT_DAYS), unconfirmed);
    }

    public MeetingVOs.Page page(MeetingDTOs.Query q) {
        LambdaQueryWrapper<ShiftMeeting> w = Wrappers.<ShiftMeeting>lambdaQuery()
                .eq(q.getTeamId() != null, ShiftMeeting::getTeamId, q.getTeamId())
                .ge(q.getFrom() != null, ShiftMeeting::getMeetingDate, q.getFrom())
                .le(q.getTo() != null, ShiftMeeting::getMeetingDate, q.getTo())
                .gt(Boolean.TRUE.equals(q.getUnfit()), ShiftMeeting::getUnfitCount, 0)
                .orderByDesc(ShiftMeeting::getMeetingDate).orderByDesc(ShiftMeeting::getCreateTime);
        var page = meetingMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(q.getCurrent(), q.getSize()), w);
        fillConfirmed(page.getRecords());
        page.getRecords().forEach(m -> m.setTopics(cut(m.getTopics(), 60)));
        return new MeetingVOs.Page(page.getRecords(), page.getTotal());
    }

    public MeetingVOs.Detail detail(Long id) {
        ShiftMeeting m = loadMeeting(id);
        List<MeetingAttendee> list = attendees(id);
        Long me = StpUtil.getLoginIdAsLong();
        boolean attendee = list.stream().anyMatch(a -> a.getUserId().equals(me));
        if (!attendee && !StpUtil.hasPermission(PERM_LIST)) {
            throw new BizException(404, "班前会不存在");
        }
        m.setConfirmedCount((int) list.stream().filter(a -> a.getConfirmTime() != null).count());
        boolean canConfirm = list.stream().anyMatch(a -> a.getUserId().equals(me) && MeetingRules.PRESENT.equals(a.getAttend()) && a.getConfirmTime() == null);
        return new MeetingVOs.Detail(m, list, canConfirm, canHost(m.getHostId()));
    }

    /**
     * 开班前会：班组长（或管理员）点名、写安全要点。一个班组一天一个班次只能开一次。
     * 状态不好的人通知安全员；出席的人收到“请确认”的待办。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long hold(MeetingDTOs.Save dto) {
        MeetingTeam t = loadTeam(dto.getTeamId());
        if (!Integer.valueOf(1).equals(t.getStatus())) {
            throw new BizException("班组已停用");
        }
        if (!canHost(t.getLeaderId())) {
            throw new BizException(403, "只有班组长和安全管理员能开这个班组的班前会");
        }
        String error = firstError(MeetingRules.checkDate(dto.getMeetingDate(), LocalDate.now()), MeetingRules.checkTopics(dto.getTopics()),
                MeetingRules.SHIFTS.containsKey(dto.getShift()) ? null : "班次不正确");
        if (error != null) {
            throw new BizException(error);
        }
        Map<Long, MeetingTeamMember> members = members(t.getId()).stream()
                .collect(Collectors.toMap(MeetingTeamMember::getUserId, x -> x, (a, b) -> a));
        List<MeetingRules.Roll> roll = dto.getRoll().stream().map(r -> new MeetingRules.Roll(r.getUserId(), r.getAttend(),
                MeetingRules.PRESENT.equals(r.getAttend()) ? r.getBodyState() : null)).toList();
        error = MeetingRules.checkRoll(members.keySet(), roll);
        if (error != null) {
            throw new BizException(error);
        }
        if (meetingMapper.selectCount(Wrappers.<ShiftMeeting>lambdaQuery().eq(ShiftMeeting::getTeamId, t.getId())
                .eq(ShiftMeeting::getMeetingDate, dto.getMeetingDate()).eq(ShiftMeeting::getShift, dto.getShift())) > 0) {
            throw new BizException(dto.getMeetingDate() + " " + MeetingRules.SHIFTS.get(dto.getShift()) + "的班前会已经开过了");
        }
        SysUser me = userService.getById(StpUtil.getLoginIdAsLong());
        ShiftMeeting m = new ShiftMeeting();
        m.setTeamId(t.getId());
        m.setTeamName(t.getName());
        m.setMeetingDate(dto.getMeetingDate());
        m.setShift(dto.getShift());
        m.setHostId(me.getId());
        m.setHostName(me.getNickname());
        m.setTopics(dto.getTopics().trim());
        m.setAiUsed(dto.isAiUsed() ? 1 : 0);
        m.setMemberCount(roll.size());
        m.setPresentCount((int) roll.stream().filter(r -> MeetingRules.PRESENT.equals(r.attend())).count());
        m.setUnfitCount((int) roll.stream().filter(r -> MeetingRules.unfit(r.attend(), r.condition())).count());
        try {
            meetingMapper.insert(m);
        } catch (DuplicateKeyException e) {
            throw new BizException("这个班次的班前会已经开过了");
        }
        List<String> unfit = new ArrayList<>();
        List<Long> toConfirm = new ArrayList<>();
        for (MeetingDTOs.RollItem r : dto.getRoll()) {
            boolean present = MeetingRules.PRESENT.equals(r.getAttend());
            MeetingAttendee a = new MeetingAttendee();
            a.setMeetingId(m.getId());
            a.setUserId(r.getUserId());
            a.setUserName(members.get(r.getUserId()).getUserName());
            a.setAttend(r.getAttend());
            a.setBodyState(present ? r.getBodyState() : null);
            a.setNote(StringUtils.hasText(r.getNote()) ? r.getNote().trim() : null);
            // 主持人自己默认已确认
            if (present && r.getUserId().equals(me.getId())) {
                a.setConfirmTime(LocalDateTime.now());
            }
            attendeeMapper.insert(a);
            if (MeetingRules.unfit(a.getAttend(), a.getBodyState())) {
                unfit.add(a.getUserName() + "（" + MeetingRules.CONDITIONS.get(a.getBodyState()) + "）");
            } else if (present && a.getConfirmTime() == null) {
                toConfirm.add(a.getUserId());
            }
        }
        if (!toConfirm.isEmpty()) {
            messageService.send(toConfirm, MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, m.getId(),
                    "请确认班前会：" + t.getName() + " " + MeetingRules.SHIFTS.get(m.getShift()),
                    "请阅读今天的安全要点并确认：" + cut(m.getTopics(), 80), LINK));
        }
        if (!unfit.isEmpty()) {
            List<Long> safety = messageService.listPermissionUsers(PERM_EDIT).stream().filter(u -> !u.equals(me.getId())).toList();
            if (!safety.isEmpty()) {
                messageService.send(safety, MessageDraft.of(MessageCategory.WARN, BIZ_TYPE, m.getId(),
                        t.getName() + "：" + unfit.size() + " 人状态不好，今天不能上岗",
                        String.join("、", unfit) + "。班组长：" + me.getNickname() + "，请安排顶岗", LINK));
            }
        }
        return m.getId();
    }

    /** 成员确认“已参加、清楚今天的安全要点” */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long meetingId) {
        loadMeeting(meetingId);
        MeetingAttendee a = attendeeMapper.selectOne(Wrappers.<MeetingAttendee>lambdaQuery()
                .eq(MeetingAttendee::getMeetingId, meetingId).eq(MeetingAttendee::getUserId, StpUtil.getLoginIdAsLong()));
        if (a == null) {
            throw new BizException(404, "你不在这次班前会的点名里");
        }
        if (!MeetingRules.PRESENT.equals(a.getAttend())) {
            throw new BizException("你这次是" + MeetingRules.ATTENDS.get(a.getAttend()) + "，不需要确认");
        }
        if (a.getConfirmTime() != null) {
            throw new BizException("已经确认过了");
        }
        a.setConfirmTime(LocalDateTime.now());
        attendeeMapper.updateById(a);
    }

    /** 手机端：我当班组长的班组今天的情况、我还没确认的班前会（3 天内） */
    public MeetingVOs.Mine mine() {
        Long me = StpUtil.getLoginIdAsLong();
        List<MeetingTeam> leading = teamMapper.selectList(Wrappers.<MeetingTeam>lambdaQuery()
                .eq(MeetingTeam::getStatus, 1).eq(MeetingTeam::getLeaderId, me).orderByAsc(MeetingTeam::getName));
        List<MeetingAttendee> mine = attendeeMapper.selectList(Wrappers.<MeetingAttendee>lambdaQuery()
                .eq(MeetingAttendee::getUserId, me).eq(MeetingAttendee::getAttend, MeetingRules.PRESENT)
                .isNull(MeetingAttendee::getConfirmTime).ge(MeetingAttendee::getCreateTime, LocalDate.now().minusDays(3).atStartOfDay()));
        List<MeetingVOs.Pending> pending = new ArrayList<>();
        if (!mine.isEmpty()) {
            Map<Long, ShiftMeeting> ms = meetingMapper.selectBatchIds(mine.stream().map(MeetingAttendee::getMeetingId).toList())
                    .stream().collect(Collectors.toMap(ShiftMeeting::getId, x -> x));
            mine.stream().filter(a -> ms.containsKey(a.getMeetingId()))
                    .forEach(a -> pending.add(new MeetingVOs.Pending(ms.get(a.getMeetingId()), a)));
        }
        return new MeetingVOs.Mine(buildTeams(leading, true), pending);
    }

    /** 手机首页：我当班组长、今天还没开班前会的班组数 + 我没确认的；都不是返回 null */
    public Integer myTodo() {
        MeetingVOs.Mine m = mine();
        if (m.leading().isEmpty() && m.pending().isEmpty()) {
            return null;
        }
        return (int) m.leading().stream().filter(t -> !t.today()).count() + m.pending().size();
    }

    /** AI 生成安全要点前校验：班组存在、当前用户是班组长或管理员 */
    public MeetingTeam teamForHost(Long teamId) {
        MeetingTeam t = loadTeam(teamId);
        if (!canHost(t.getLeaderId())) {
            throw new BizException(403, "只有班组长和安全管理员能给这个班组准备班前会");
        }
        return t;
    }

    // ============================== 定时任务 / 大屏（没有登录用户） ==============================

    /** 今天还没开班前会的启用班组 */
    public List<MeetingTeam> notHeldToday() {
        LocalDate today = LocalDate.now();
        Set<Long> held = meetingMapper.selectList(Wrappers.<ShiftMeeting>lambdaQuery().eq(ShiftMeeting::getMeetingDate, today)
                        .select(ShiftMeeting::getTeamId))
                .stream().map(ShiftMeeting::getTeamId).collect(Collectors.toSet());
        return teamMapper.selectList(Wrappers.<MeetingTeam>lambdaQuery().eq(MeetingTeam::getStatus, 1))
                .stream().filter(t -> !held.contains(t.getId())).toList();
    }

    /** 今天出席但状态不好、不能上岗的人数 */
    public int unfitToday() {
        return meetingMapper.selectList(Wrappers.<ShiftMeeting>lambdaQuery().eq(ShiftMeeting::getMeetingDate, LocalDate.now())
                        .select(ShiftMeeting::getUnfitCount))
                .stream().mapToInt(m -> m.getUnfitCount() == null ? 0 : m.getUnfitCount()).sum();
    }

    /** 附件权限用：能不能看 / 传这次班前会的照片 */
    public boolean canView(Long meetingId) {
        loadMeeting(meetingId);
        return StpUtil.hasPermission(PERM_LIST) || attendeeMapper.selectCount(Wrappers.<MeetingAttendee>lambdaQuery()
                .eq(MeetingAttendee::getMeetingId, meetingId).eq(MeetingAttendee::getUserId, StpUtil.getLoginIdAsLong())) > 0;
    }

    public boolean canEditPhotos(Long meetingId) {
        return canHost(loadMeeting(meetingId).getHostId());
    }

    // ============================== 内部方法 ==============================

    private List<MeetingVOs.Team> buildTeams(List<MeetingTeam> teams, boolean withAccess) {
        if (teams.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<Long> ids = teams.stream().map(MeetingTeam::getId).toList();
        Map<Long, List<MeetingTeamMember>> members = memberMapper.selectList(Wrappers.<MeetingTeamMember>lambdaQuery()
                        .in(MeetingTeamMember::getTeamId, ids).orderByAsc(MeetingTeamMember::getId))
                .stream().collect(Collectors.groupingBy(MeetingTeamMember::getTeamId));
        Map<Long, List<LocalDate>> dates = meetingMapper.selectList(Wrappers.<ShiftMeeting>lambdaQuery()
                        .in(ShiftMeeting::getTeamId, ids).ge(ShiftMeeting::getMeetingDate, today.minusDays(MeetingRules.STAT_DAYS - 1))
                        .select(ShiftMeeting::getTeamId, ShiftMeeting::getMeetingDate))
                .stream().collect(Collectors.groupingBy(ShiftMeeting::getTeamId,
                        Collectors.mapping(ShiftMeeting::getMeetingDate, Collectors.toList())));
        return teams.stream().map(t -> {
            List<LocalDate> d = dates.getOrDefault(t.getId(), List.of());
            int held = (int) d.stream().distinct().count();
            LocalDate last = d.stream().max(LocalDate::compareTo).orElse(null);
            return new MeetingVOs.Team(t, members.getOrDefault(t.getId(), List.of()), held,
                    MeetingRules.rate(held, MeetingRules.STAT_DAYS), d.contains(today), last, withAccess && canHost(t.getLeaderId()));
        }).toList();
    }

    private void fillConfirmed(List<ShiftMeeting> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, Long> c = attendeeMapper.selectList(Wrappers.<MeetingAttendee>lambdaQuery()
                        .in(MeetingAttendee::getMeetingId, list.stream().map(ShiftMeeting::getId).toList())
                        .isNotNull(MeetingAttendee::getConfirmTime).select(MeetingAttendee::getMeetingId))
                .stream().collect(Collectors.groupingBy(MeetingAttendee::getMeetingId, Collectors.counting()));
        list.forEach(m -> m.setConfirmedCount(c.getOrDefault(m.getId(), 0L).intValue()));
    }

    private boolean canHost(Long leaderOrHostId) {
        return StpUtil.hasPermission(PERM_EDIT) || Objects.equals(leaderOrHostId, StpUtil.getLoginIdAsLong());
    }

    private List<MeetingTeamMember> members(Long teamId) {
        return memberMapper.selectList(Wrappers.<MeetingTeamMember>lambdaQuery().eq(MeetingTeamMember::getTeamId, teamId));
    }

    private List<MeetingAttendee> attendees(Long meetingId) {
        return attendeeMapper.selectList(Wrappers.<MeetingAttendee>lambdaQuery().eq(MeetingAttendee::getMeetingId, meetingId)
                .orderByAsc(MeetingAttendee::getId));
    }

    private SysUser activeUser(Long id) {
        SysUser u = id == null ? null : userService.getById(id);
        if (u == null) {
            throw new BizException("人员不存在");
        }
        if (!Integer.valueOf(1).equals(u.getStatus())) {
            throw new BizException("【" + u.getNickname() + "】的账号已停用");
        }
        return u;
    }

    private MeetingTeam loadTeam(Long id) {
        MeetingTeam t = teamMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "班组不存在");
        }
        return t;
    }

    private ShiftMeeting loadMeeting(Long id) {
        ShiftMeeting m = meetingMapper.selectById(id);
        if (m == null) {
            throw new BizException(404, "班前会不存在");
        }
        return m;
    }

    private static String firstError(String... errors) {
        for (String e : errors) {
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static String cut(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
