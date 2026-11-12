package com.safework.ticket.briefing;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.mapper.WorkBriefingAttendeeMapper;
import com.safework.ticket.mapper.WorkBriefingMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import com.safework.ticket.sign.SignatureImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkBriefingService {
    public static final String PERM_LIST = "briefing:list";
    public static final String PERM_MANAGE = "briefing:manage";
    public static final String PERM_SIGN = "briefing:sign";
    public static final String LINK = "/ticket/list";

    private final WorkBriefingMapper mapper;
    private final WorkBriefingAttendeeMapper attendeeMapper;
    private final WorkTicketMapper ticketMapper;
    private final UserService userService;
    private final MessageService messageService;

    public Page<WorkBriefingVOs.Row> page(WorkBriefingDTOs.Query q) {
        long current = Math.max(1, q.getCurrent());
        long size = Math.min(100, Math.max(1, q.getSize()));
        boolean manager = StpUtil.hasPermission(PERM_MANAGE);
        Long me = StpUtil.getLoginIdAsLong();
        List<Long> myBriefingIds = List.of();
        if (!manager) {
            myBriefingIds = attendeeMapper.selectList(Wrappers.<WorkBriefingAttendee>lambdaQuery()
                            .select(WorkBriefingAttendee::getBriefingId)
                            .eq(WorkBriefingAttendee::getParticipantId, me))
                    .stream().map(WorkBriefingAttendee::getBriefingId).distinct().toList();
            if (myBriefingIds.isEmpty()) return new Page<>(current, size, 0);
        }
        String keyword = q.getKeyword();
        var source = mapper.selectPage(new Page<WorkBriefing>(current, size), Wrappers.<WorkBriefing>lambdaQuery()
                .eq(StringUtils.hasText(q.getStatus()), WorkBriefing::getStatus, q.getStatus())
                .in(!manager, WorkBriefing::getId, myBriefingIds)
                .and(StringUtils.hasText(keyword), w -> w.like(WorkBriefing::getTicketNo, keyword)
                        .or().like(WorkBriefing::getTitle, keyword))
                .orderByDesc(WorkBriefing::getCreateTime));
        List<WorkBriefingVOs.Row> rows = source.getRecords().stream().map(this::row).toList();
        Page<WorkBriefingVOs.Row> result = new Page<>(current, size, source.getTotal());
        result.setRecords(rows);
        return result;
    }

    public WorkBriefingVOs.Detail byTicket(Long ticketId) {
        if (ticketMapper.selectById(ticketId) == null) throw new BizException("作业票不存在");
        WorkBriefing briefing = mapper.selectOne(Wrappers.<WorkBriefing>lambdaQuery().eq(WorkBriefing::getTicketId, ticketId));
        return briefing == null ? null : detail(briefing.getId());
    }

    public WorkBriefingVOs.Detail detail(Long id) {
        WorkBriefing briefing = load(id);
        Long me = StpUtil.getLoginIdAsLong();
        boolean manager = StpUtil.hasPermission(PERM_MANAGE);
        List<WorkBriefingAttendee> people = attendees(id);
        if (!manager && people.stream().noneMatch(a -> me.equals(a.getParticipantId()))) {
            throw new BizException("作业交底不存在");
        }
        return view(briefing, people, manager, me);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkBriefingDTOs.Create dto) {
        WorkTicket ticket = ticketMapper.selectById(dto.getTicketId());
        if (ticket == null) throw new BizException("作业票不存在");
        if (ticket.getStatus() != TicketStatus.APPROVED) throw new BizException("只有已批准、尚未开工的作业票才能创建交底");
        if (Integer.valueOf(1).equals(ticket.getBriefingRequired())) throw new BizException("这张作业票已经启用安全交底");
        Map<Long, String> people = new LinkedHashMap<>();
        people.put(ticket.getApplicantId(), WorkBriefingRules.APPLICANT);
        people.put(ticket.getGuardianId(), WorkBriefingRules.GUARDIAN);
        if (dto.getAttendeeIds() != null) {
            for (Long userId : dto.getAttendeeIds()) {
                if (userId == null) continue;
                people.putIfAbsent(userId, WorkBriefingRules.MEMBER);
            }
        }
        if (people.isEmpty() || people.size() > 32) throw new BizException("参与人员数量不正确");
        List<SysUser> users = new ArrayList<>();
        for (Long userId : people.keySet()) {
            SysUser user = userService.getById(userId);
            if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
                throw new BizException("有参与人员账号不存在或已停用，请重新选择");
            }
            users.add(user);
        }

        Long me = StpUtil.getLoginIdAsLong();
        SysUser creator = userService.getById(me);
        WorkBriefing briefing = new WorkBriefing();
        briefing.setTicketId(ticket.getId());
        briefing.setTicketNo(ticket.getTicketNo());
        briefing.setTitle(ticket.getTitle());
        briefing.setContentSnapshot(WorkBriefingRules.snapshot(ticket.getTicketNo(), ticket.getTitle(),
                ticket.getTicketType().getLabel(), ticket.getLocation(), ticket.getWorkContent(),
                ticket.getJsaTitle(), ticket.getSafetyMeasures()));
        briefing.setStatus(WorkBriefingRules.PENDING);
        briefing.setCreatorId(me);
        briefing.setCreatorName(creator == null ? StpUtil.getLoginId() : creator.getNickname());
        mapper.insert(briefing);

        for (SysUser user : users) {
            WorkBriefingAttendee attendee = new WorkBriefingAttendee();
            attendee.setBriefingId(briefing.getId());
            attendee.setTicketId(ticket.getId());
            attendee.setParticipantId(user.getId());
            attendee.setParticipantName(user.getNickname());
            attendee.setParticipantRole(people.get(user.getId()));
            attendeeMapper.insert(attendee);
        }

        ticket.setBriefingRequired(1);
        ticketMapper.updateById(ticket);
        List<Long> recipients = users.stream().map(SysUser::getId).distinct().toList();
        messageService.send(recipients, MessageDraft.of(MessageCategory.TODO, "WORK_BRIEFING", briefing.getId(),
                "请完成作业安全交底", "作业票 " + ticket.getTicketNo() + "《" + ticket.getTitle() + "》开工前需要全员签字确认。",
                LINK + "?detail=" + ticket.getId()));
        return briefing.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, String signature) {
        Long me = StpUtil.getLoginIdAsLong();
        WorkBriefing briefing = load(id);
        if (!WorkBriefingRules.PENDING.equals(briefing.getStatus())) throw new BizException("这份交底已经完成");
        WorkBriefingAttendee attendee = attendeeMapper.selectOne(Wrappers.<WorkBriefingAttendee>lambdaQuery()
                .eq(WorkBriefingAttendee::getBriefingId, id).eq(WorkBriefingAttendee::getParticipantId, me));
        if (attendee == null) throw new BizException("你不是本次交底的参与人员");
        if (attendee.getSignedTime() != null) throw new BizException("你已经签字确认");
        SignatureImage.Checked checked;
        try {
            checked = SignatureImage.check(signature);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = attendeeMapper.update(null, Wrappers.<WorkBriefingAttendee>lambdaUpdate()
                .set(WorkBriefingAttendee::getSignedTime, now)
                .set(WorkBriefingAttendee::getSignImage, checked.dataUrl())
                .set(WorkBriefingAttendee::getSignHash, checked.sha256())
                .eq(WorkBriefingAttendee::getId, attendee.getId())
                .isNull(WorkBriefingAttendee::getSignedTime));
        if (changed != 1) throw new BizException("签名状态已变化，请刷新后查看");
        long total = attendeeMapper.selectCount(Wrappers.<WorkBriefingAttendee>lambdaQuery().eq(WorkBriefingAttendee::getBriefingId, id));
        long signed = attendeeMapper.selectCount(Wrappers.<WorkBriefingAttendee>lambdaQuery()
                .eq(WorkBriefingAttendee::getBriefingId, id).isNotNull(WorkBriefingAttendee::getSignedTime));
        if (WorkBriefingRules.canStart(briefing.getStatus(), (int) total, (int) signed)) {
            briefing.setStatus(WorkBriefingRules.COMPLETED);
            briefing.setCompletedTime(now);
            mapper.updateById(briefing);
        }
    }

    public void requireComplete(Long ticketId) {
        WorkBriefing briefing = mapper.selectOne(Wrappers.<WorkBriefing>lambdaQuery().eq(WorkBriefing::getTicketId, ticketId));
        if (briefing == null) throw new BizException("作业安全交底尚未创建，完成交底签字后才能开工");
        long total = attendeeMapper.selectCount(Wrappers.<WorkBriefingAttendee>lambdaQuery().eq(WorkBriefingAttendee::getBriefingId, briefing.getId()));
        long signed = attendeeMapper.selectCount(Wrappers.<WorkBriefingAttendee>lambdaQuery()
                .eq(WorkBriefingAttendee::getBriefingId, briefing.getId()).isNotNull(WorkBriefingAttendee::getSignedTime));
        if (!WorkBriefingRules.canStart(briefing.getStatus(), (int) total, (int) signed)) {
            throw new BizException("作业安全交底还未收齐所有签名，暂时不能开工");
        }
    }

    private WorkBriefingVOs.Row row(WorkBriefing b) {
        List<WorkBriefingAttendee> people = attendees(b.getId());
        int signed = (int) people.stream().filter(a -> a.getSignedTime() != null).count();
        return new WorkBriefingVOs.Row(b, people.size(), signed);
    }

    private WorkBriefingVOs.Detail view(WorkBriefing b, List<WorkBriefingAttendee> people, boolean manager, Long me) {
        return new WorkBriefingVOs.Detail(b, people.stream().map(a -> new WorkBriefingVOs.Attendee(a.getId(),
                a.getParticipantId(), a.getParticipantName(), a.getParticipantRole(), a.getSignedTime(),
                a.getSignImage(), me.equals(a.getParticipantId()))).toList(), manager);
    }

    private List<WorkBriefingAttendee> attendees(Long briefingId) {
        return attendeeMapper.selectList(Wrappers.<WorkBriefingAttendee>lambdaQuery()
                .eq(WorkBriefingAttendee::getBriefingId, briefingId).orderByAsc(WorkBriefingAttendee::getId));
    }

    private WorkBriefing load(Long id) {
        WorkBriefing b = mapper.selectById(id);
        if (b == null) throw new BizException("作业交底不存在");
        return b;
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
}