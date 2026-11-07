package com.safework.ticket.visit;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.VisitPass;
import com.safework.ticket.mapper.VisitPassMapper;
import com.safework.ticket.sign.SignatureImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 外来人员与车辆入厂：员工预约 → 危化品车辆安全员审批 → 门岗当天核对、来访人签《入厂安全须知》→ 入厂 → 离厂。
 * <p>
 * 数据范围：有门岗或审批权限的人看全部；其他员工只看自己登记的、自己接待的。
 * expireOld / overstayed / overview 不用登录信息，定时任务和大屏可以调用。
 */
@Service
@RequiredArgsConstructor
public class VisitService {

    public static final String PERM_LIST = "visit:list";
    public static final String PERM_GATE = "visit:gate";
    public static final String PERM_APPROVE = "visit:approve";
    public static final String LINK = "/safety/visit";
    public static final String BIZ_TYPE = "VISIT";

    private final VisitPassMapper mapper;
    private final SerialNoGenerator serialNo;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 预约 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long create(VisitDTOs.Create d) {
        LocalDate today = LocalDate.now();
        if (!VisitRules.TYPES.containsKey(d.getVisitType())) {
            throw new BizException("类型不正确");
        }
        String err = VisitRules.checkPlanDate(d.getPlanDate(), today);
        if (err != null) {
            throw new BizException(err);
        }
        if (!VisitRules.validPhone(d.getPhone())) {
            throw new BizException("手机号不正确");
        }
        String idCard = d.getIdCard().trim();
        if (!VisitRules.validIdCard(idCard)) {
            throw new BizException("身份证号不正确（18 位，最后一位校验码不对）");
        }
        boolean vehicle = !VisitRules.VISITOR.equals(d.getVisitType());
        String plate = StringUtils.hasText(d.getPlateNo()) ? d.getPlateNo().trim().toUpperCase() : null;
        if (vehicle && !VisitRules.validPlate(plate)) {
            throw new BizException("车牌号不正确，例如 皖A12345");
        }
        if (plate != null && !VisitRules.validPlate(plate)) {
            throw new BizException("车牌号不正确，例如 皖A12345");
        }
        SysUser me = me();
        SysUser host = d.getHostId() == null ? me : userService.getById(d.getHostId());
        VisitPass p = new VisitPass();
        p.setPassNo(serialNo.next("RC"));
        p.setVisitType(d.getVisitType());
        p.setVisitorName(d.getVisitorName().trim());
        p.setPhone(d.getPhone().trim());
        p.setIdMasked(VisitRules.maskId(idCard));
        p.setCompany(d.getCompany().trim());
        p.setPurpose(d.getPurpose().trim());
        p.setPeopleCount(vehicle ? Math.min(d.getPeopleCount(), 3) : d.getPeopleCount());
        p.setPlateNo(plate);
        p.setHostId(host.getId());
        p.setHostName(host.getNickname());
        p.setCreatorId(me.getId());
        p.setCreatorName(me.getNickname());
        p.setPlanDate(d.getPlanDate());
        p.setStatus(VisitRules.initialStatus(d.getVisitType()));
        if (VisitRules.HAZMAT.equals(d.getVisitType())) {
            p.setDriverCertNo(trim(d.getDriverCertNo()));
            p.setDriverCertExpire(d.getDriverCertExpire());
            p.setEscortName(trim(d.getEscortName()));
            p.setEscortCertNo(trim(d.getEscortCertNo()));
            p.setEscortCertExpire(d.getEscortCertExpire());
            p.setTransportPermitNo(trim(d.getTransportPermitNo()));
            p.setTransportPermitExpire(d.getTransportPermitExpire());
            p.setCargoName(trim(d.getCargoName()));
            p.setCargoUnNo(trim(d.getCargoUnNo()));
            p.setCargoQty(d.getCargoQty());
            p.setDestination(trim(d.getDestination()));
            if (p.getEscortName() == null || p.getCargoName() == null) {
                throw new BizException("危化品运输车辆要填写押运员和运输的货物");
            }
        }
        p.setRemark(trim(d.getRemark()));
        mapper.insert(p);
        if (VisitRules.PENDING.equals(p.getStatus())) {
            List<Long> to = messageService.listPermissionUsers(PERM_APPROVE).stream().filter(u -> !u.equals(me.getId())).toList();
            messageService.send(to, MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, p.getId(), "危化品车辆入厂待审批：" + p.getPlateNo(),
                    p.getPlanDate() + " " + p.getCompany() + " " + p.getCargoName() + " " + p.getCargoQty() + " 吨，" + me.getNickname() + " 登记", LINK));
        }
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        VisitPass p = load(id);
        SysUser me = me();
        if (Objects.equals(p.getCreatorId(), me.getId())) {
            throw new BizException("不能审批自己登记的预约");
        }
        p.setApproverName(me.getNickname());
        p.setApproveTime(LocalDateTime.now());
        fire(p, VisitRules.Action.APPROVE);
        notifyUser(p.getCreatorId(), "危化品车辆入厂已审批通过：" + p.getPlateNo(), p.getPlanDate() + " 可以入厂，门岗还会再核对证件");
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        VisitPass p = load(id);
        SysUser me = me();
        p.setApproverName(me.getNickname());
        p.setApproveTime(LocalDateTime.now());
        p.setRejectReason(reason.trim());
        fire(p, VisitRules.Action.REJECT);
        notifyUser(p.getCreatorId(), "危化品车辆入厂被驳回：" + p.getPlateNo(), reason.trim());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        VisitPass p = load(id);
        if (!canCancel(p)) {
            throw new BizException(403, "只有登记人、接待人或门岗可以取消");
        }
        fire(p, VisitRules.Action.CANCEL);
    }

    // ============================== 门岗 ==============================

    /** 入厂：当天、危化品车辆证件都有效、来访人签了《入厂安全须知》 */
    @Transactional(rollbackFor = Exception.class)
    public VisitVOs.EnterResult enter(Long id, String signImage) {
        VisitPass p = load(id);
        if (!VisitRules.Action.ENTER.canFire(p.getStatus())) {
            throw new BizException(VisitRules.PENDING.equals(p.getStatus()) ? "危化品车辆还没审批通过，不能入厂" : "当前状态【" + VisitRules.STATUS.get(p.getStatus()) + "】不能入厂");
        }
        LocalDate today = LocalDate.now();
        String err = VisitRules.checkEnterDay(p.getPlanDate(), today);
        if (err != null) {
            throw new BizException(err);
        }
        List<String> problems = problems(p, today);
        if (!problems.isEmpty()) {
            throw new BizException("不能入厂：" + String.join("；", problems));
        }
        SignatureImage.Checked sign;
        try {
            sign = SignatureImage.check(signImage);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        SysUser me = me();
        p.setSignImage(sign.dataUrl());
        p.setSignHash(sign.sha256());
        p.setSignTime(LocalDateTime.now());
        p.setEnterTime(LocalDateTime.now());
        p.setEnterGuard(me.getNickname());
        fire(p, VisitRules.Action.ENTER);
        notifyUser(p.getHostId(), (VisitRules.VISITOR.equals(p.getVisitType()) ? "你的访客已入厂：" + p.getVisitorName() : "车辆已入厂：" + p.getPlateNo()),
                p.getCompany() + "，" + p.getPurpose() + "。请到门岗接待");
        String tip = VisitRules.HAZMAT.equals(p.getVisitType())
                ? "请引导车辆按指定路线到" + (p.getDestination() == null ? "卸货点" : p.getDestination()) + "，卸车前熄火、接好静电接地线；卸完到危化品出入库办理入库"
                : null;
        return new VisitVOs.EnterResult(p.getPassNo(), tip);
    }

    @Transactional(rollbackFor = Exception.class)
    public void leave(Long id) {
        VisitPass p = load(id);
        p.setLeaveTime(LocalDateTime.now());
        p.setLeaveGuard(me().getNickname());
        fire(p, VisitRules.Action.LEAVE);
    }

    // ============================== 查询 ==============================

    public VisitVOs.Page page(VisitDTOs.Query q) {
        LocalDate today = LocalDate.now();
        String kw = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null;
        LambdaQueryWrapper<VisitPass> w = scope(Wrappers.<VisitPass>lambdaQuery())
                .eq(q.getDate() != null, VisitPass::getPlanDate, q.getDate())
                .eq(StringUtils.hasText(q.getStatus()), VisitPass::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getVisitType()), VisitPass::getVisitType, q.getVisitType())
                .and(kw != null, x -> x.like(VisitPass::getVisitorName, kw).or().like(VisitPass::getCompany, kw).or().like(VisitPass::getPlateNo, kw)
                        .or().like(VisitPass::getPassNo, kw).or().likeLeft(VisitPass::getPhone, kw))
                .orderByDesc(VisitPass::getPlanDate).orderByDesc(VisitPass::getCreateTime);
        Page<VisitPass> page = mapper.selectPage(Page.of(q.getCurrent(), Math.min(q.getSize(), 100)), w);
        return new VisitVOs.Page(page.getRecords().stream().map(p -> row(p, today)).toList(), page.getTotal());
    }

    /** 门岗：今天要来的 + 在厂的（含以前没离厂的） */
    public List<VisitVOs.Row> gate() {
        LocalDate today = LocalDate.now();
        return mapper.selectList(Wrappers.<VisitPass>lambdaQuery()
                        .and(w -> w.eq(VisitPass::getStatus, VisitRules.ENTERED)
                                .or(x -> x.eq(VisitPass::getPlanDate, today).in(VisitPass::getStatus, VisitRules.PENDING, VisitRules.APPROVED)))
                        .orderByAsc(VisitPass::getStatus).orderByAsc(VisitPass::getCreateTime))
                .stream().map(p -> row(p, today)).toList();
    }

    /** 在厂清单（应急清点用） */
    public List<VisitPass> onsite() {
        return mapper.selectList(Wrappers.<VisitPass>lambdaQuery().eq(VisitPass::getStatus, VisitRules.ENTERED).orderByAsc(VisitPass::getEnterTime));
    }

    public VisitVOs.Detail detail(Long id) {
        VisitPass p = load(id);
        if (!seeAll() && !isMine(p)) {
            throw new BizException(404, "预约不存在");
        }
        p.setSignImage(mapper.signImage(id));
        LocalDate today = LocalDate.now();
        boolean gate = StpUtil.hasPermission(PERM_GATE);
        boolean approve = StpUtil.hasPermission(PERM_APPROVE) && VisitRules.Action.APPROVE.canFire(p.getStatus())
                && !Objects.equals(p.getCreatorId(), StpUtil.getLoginIdAsLong());
        return new VisitVOs.Detail(p, problems(p, today), VisitRules.overstay(p.getStatus(), p.getPlanDate(), today), VisitRules.NOTICE,
                approve, VisitRules.Action.CANCEL.canFire(p.getStatus()) && canCancel(p), gate);
    }

    public VisitVOs.Overview overview() {
        LocalDate today = LocalDate.now();
        List<VisitPass> on = onsite();
        int today_ = Math.toIntExact(mapper.selectCount(Wrappers.<VisitPass>lambdaQuery().eq(VisitPass::getPlanDate, today)
                .notIn(VisitPass::getStatus, VisitRules.CANCELLED, VisitRules.REJECTED)));
        int pending = Math.toIntExact(mapper.selectCount(Wrappers.<VisitPass>lambdaQuery().eq(VisitPass::getStatus, VisitRules.PENDING)));
        return new VisitVOs.Overview(today_, on.stream().mapToInt(p -> p.getPeopleCount() == null ? 1 : p.getPeopleCount()).sum(),
                (int) on.stream().filter(p -> VisitRules.VISITOR.equals(p.getVisitType())).count(),
                (int) on.stream().filter(p -> !VisitRules.VISITOR.equals(p.getVisitType())).count(),
                (int) on.stream().filter(p -> VisitRules.HAZMAT.equals(p.getVisitType())).count(), pending,
                (int) on.stream().filter(p -> VisitRules.overstay(p.getStatus(), p.getPlanDate(), today)).count());
    }

    // ============================== 定时任务 ==============================

    /** 过了预约日期还没入厂的（含没审批的）标记为过期，返回条数 */
    @Transactional(rollbackFor = Exception.class)
    public int expireOld(LocalDate today) {
        List<VisitPass> list = mapper.selectList(Wrappers.<VisitPass>lambdaQuery().lt(VisitPass::getPlanDate, today)
                .in(VisitPass::getStatus, VisitRules.PENDING, VisitRules.APPROVED));
        list.forEach(p -> {
            p.setStatus(VisitRules.EXPIRED);
            mapper.updateById(p);
        });
        return list.size();
    }

    public List<VisitPass> overstayed(LocalDate today) {
        return mapper.selectList(Wrappers.<VisitPass>lambdaQuery().eq(VisitPass::getStatus, VisitRules.ENTERED).lt(VisitPass::getPlanDate, today));
    }

    // ============================== 内部 ==============================

    private VisitVOs.Row row(VisitPass p, LocalDate today) {
        return new VisitVOs.Row(p, problems(p, today), VisitRules.overstay(p.getStatus(), p.getPlanDate(), today));
    }

    /** 危化品车辆入厂检查（按预约日期和今天中晚的那天算证件有效期） */
    private List<String> problems(VisitPass p, LocalDate today) {
        if (!VisitRules.HAZMAT.equals(p.getVisitType()) || VisitRules.LEFT.equals(p.getStatus()) || VisitRules.ENTERED.equals(p.getStatus())) {
            return List.of();
        }
        LocalDate day = p.getPlanDate().isAfter(today) ? p.getPlanDate() : today;
        return VisitRules.hazmatProblems(new VisitRules.Hazmat(p.getPlateNo(), p.getVisitorName(), p.getDriverCertNo(), p.getDriverCertExpire(),
                p.getEscortName(), p.getEscortCertNo(), p.getEscortCertExpire(), p.getTransportPermitNo(), p.getTransportPermitExpire(),
                p.getCargoName(), p.getCargoQty()), day);
    }

    private void fire(VisitPass p, VisitRules.Action a) {
        if (!a.canFire(p.getStatus())) {
            throw new BizException("当前状态【" + VisitRules.STATUS.get(p.getStatus()) + "】不能" + a.label());
        }
        p.setStatus(a.to());
        mapper.updateById(p);
    }

    private LambdaQueryWrapper<VisitPass> scope(LambdaQueryWrapper<VisitPass> w) {
        if (seeAll()) {
            return w;
        }
        Long me = StpUtil.getLoginIdAsLong();
        return w.and(x -> x.eq(VisitPass::getCreatorId, me).or().eq(VisitPass::getHostId, me));
    }

    private boolean seeAll() {
        return StpUtil.hasPermission(PERM_GATE) || StpUtil.hasPermission(PERM_APPROVE);
    }

    private boolean isMine(VisitPass p) {
        Long me = StpUtil.getLoginIdAsLong();
        return me.equals(p.getCreatorId()) || me.equals(p.getHostId());
    }

    private boolean canCancel(VisitPass p) {
        return isMine(p) || StpUtil.hasPermission(PERM_GATE);
    }

    private VisitPass load(Long id) {
        VisitPass p = mapper.selectById(id);
        if (p == null) {
            throw new BizException(404, "预约不存在");
        }
        return p;
    }

    private void notifyUser(Long userId, String title, String content) {
        if (userId != null) {
            messageService.send(List.of(userId), MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE, null, title, content, LINK));
        }
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
