package com.safework.ticket.plan;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.mapper.WorkTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JobPlanService {
    public static final String PERM_LIST = "job:plan:list";
    public static final String PERM_MANAGE = "job:plan:manage";

    private final JobPlanMapper mapper;
    private final WorkTicketMapper ticketMapper;
    private final UserService userService;
    private final SerialNoGenerator serialNo;

    public Page<JobPlan> page(JobPlanDTOs.Query q) {
        long current = Math.max(1, q.getCurrent());
        long size = Math.min(100, Math.max(1, q.getSize()));
        boolean manager = StpUtil.hasPermission(PERM_MANAGE);
        String keyword = q.getKeyword() == null ? null : q.getKeyword().trim();
        return mapper.selectPage(new Page<>(current, size), Wrappers.<JobPlan>lambdaQuery()
                .eq(StringUtils.hasText(q.getStatus()), JobPlan::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getTicketType()), JobPlan::getTicketType, q.getTicketType())
                .eq(!manager, JobPlan::getResponsibleId, StpUtil.getLoginIdAsLong())
                .and(StringUtils.hasText(keyword), w -> w.like(JobPlan::getPlanNo, keyword)
                        .or().like(JobPlan::getTitle, keyword).or().like(JobPlan::getLocation, keyword)
                        .or().like(JobPlan::getDepartmentName, keyword).or().like(JobPlan::getTicketNo, keyword))
                .orderByDesc(JobPlan::getPlannedStartTime));
    }

    public JobPlan detail(Long id) {
        JobPlan plan = mapper.selectById(id);
        if (plan == null) throw new BizException("作业计划不存在");
        if (!StpUtil.hasPermission(PERM_MANAGE) && !StpUtil.getLoginIdAsLong().equals(plan.getResponsibleId())) {
            throw new BizException("作业计划不存在");
        }
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long id, JobPlanDTOs.Save dto) {
        String error = JobPlanRules.validate(dto.getTicketType(), dto.getPlannedStartTime(), dto.getPlannedEndTime());
        if (error != null) throw new BizException(error);
        SysUser responsible = userService.getById(dto.getResponsibleId());
        if (responsible == null || !Integer.valueOf(1).equals(responsible.getStatus())) {
            throw new BizException("作业负责人账号不存在或已停用");
        }
        JobPlan plan = id == null ? new JobPlan() : load(id);
        if (id != null && !JobPlanRules.PLANNED.equals(plan.getStatus())) {
            throw new BizException("只有待开始的计划可以修改");
        }
        plan.setTitle(dto.getTitle().trim());
        plan.setTicketType(dto.getTicketType());
        plan.setLocation(dto.getLocation().trim());
        plan.setWorkContent(dto.getWorkContent().trim());
        plan.setDepartmentName(trim(dto.getDepartmentName()));
        plan.setResponsibleId(responsible.getId());
        plan.setResponsibleName(responsible.getNickname());
        plan.setPlannedStartTime(dto.getPlannedStartTime());
        plan.setPlannedEndTime(dto.getPlannedEndTime());
        plan.setRemark(trim(dto.getRemark()));
        if (dto.getTicketId() == null) {
            plan.setTicketId(null);
            plan.setTicketNo(null);
        } else {
            WorkTicket ticket = ticketMapper.selectById(dto.getTicketId());
            if (ticket == null) throw new BizException("关联的作业票不存在");
            if (!ticket.getTicketType().name().equals(dto.getTicketType())) {
                throw new BizException("作业计划类型与关联作业票类型不一致");
            }
            Long used = mapper.selectCount(Wrappers.<JobPlan>lambdaQuery().eq(JobPlan::getTicketId, ticket.getId())
                    .ne(id != null, JobPlan::getId, id).ne(JobPlan::getStatus, JobPlanRules.CANCELLED));
            if (used > 0) throw new BizException("这张作业票已经关联到其他计划");
            plan.setTicketId(ticket.getId());
            plan.setTicketNo(ticket.getTicketNo());
        }
        if (id == null) {
            plan.setPlanNo(serialNo.next("JH"));
            plan.setStatus(JobPlanRules.PLANNED);
            mapper.insert(plan);
        } else {
            mapper.updateById(plan);
        }
        return plan.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void action(Long id, String action) {
        JobPlan plan = load(id);
        String next = JobPlanRules.nextStatus(plan.getStatus(), action);
        if (next == null) throw new BizException("当前状态不能执行该操作");
        LocalDateTime now = LocalDateTime.now();
        if ("start".equals(action)) plan.setActualStartTime(now);
        if ("complete".equals(action)) plan.setActualEndTime(now);
        plan.setStatus(next);
        if (mapper.updateById(plan) != 1) throw new BizException("计划已被其他人修改，请刷新后重试");
    }

    private JobPlan load(Long id) {
        JobPlan plan = mapper.selectById(id);
        if (plan == null) throw new BizException("作业计划不存在");
        return plan;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}