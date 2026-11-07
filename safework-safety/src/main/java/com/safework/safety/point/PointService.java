package com.safework.safety.point;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.cost.CostDTOs;
import com.safework.safety.cost.CostService;
import com.safework.safety.entity.PointAccount;
import com.safework.safety.entity.PointRecord;
import com.safework.safety.entity.PointRedeem;
import com.safework.safety.entity.PointReward;
import com.safework.safety.enums.HazardAction;
import com.safework.safety.mapper.PointAccountMapper;
import com.safework.safety.mapper.PointRecordMapper;
import com.safework.safety.mapper.PointRedeemMapper;
import com.safework.safety.mapper.PointRewardMapper;
import com.safework.safety.service.HazardFiredEvent;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.system.dto.UserOptionVO;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 隐患随手拍积分：
 * <pre>
 * 隐患被确认（指派整改）──事件──▶ 上报人 +10 / +50        隐患按期整改、验收通过 ──事件──▶ 整改人 +5
 * 管理员手工加减分（写原因）
 *      │ 积分账户（条件更新，不会扣成负数）+ 流水（只增不改，同一条隐患同一种原因只得一次分）
 *      ▼
 * 员工手机上兑换奖品（扣积分、扣库存）──▶ 管理员发放：奖品金额自动记入安全投入（隐患报告奖励）/ 退回：积分和库存还回去
 * 排行榜：按月、按年统计得到的积分；每月 1 日公布上月前三名
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class PointService {

    public static final String PERM_LIST = "point:list";
    public static final String PERM_EDIT = "point:edit";
    public static final String LINK = "/safety/point";
    public static final String MOBILE_LINK = "/m/points";
    public static final String BIZ_TYPE = "POINT";
    private static final List<String> EARN_TYPES = List.of(PointRules.HAZARD_REPORT, PointRules.HAZARD_RECTIFY, PointRules.MANUAL);

    private final PointAccountMapper accountMapper;
    private final PointRecordMapper recordMapper;
    private final PointRewardMapper rewardMapper;
    private final PointRedeemMapper redeemMapper;
    private final CostService costService;
    private final SerialNoGenerator serialNo;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 得分 ==============================

    /** 隐患流转事件：确认（指派）给上报人加分，按期整改验收通过给整改人加分。监听者在新事务里调用，不用登录信息 */
    @Transactional(rollbackFor = Exception.class)
    public void onHazard(HazardFiredEvent e) {
        if (e.action() == HazardAction.ASSIGN && e.reporterId() != null) {
            int p = PointRules.reportPoints(e.level());
            String reason = "上报的隐患 " + e.hazardNo() + "《" + e.title() + "》被确认为" + ("MAJOR".equals(e.level()) ? "重大" : "一般") + "隐患";
            if (award(e.reporterId(), e.reporterName(), p, PointRules.HAZARD_REPORT, e.hazardId(), reason, e.operatorName())) {
                notifyUser(e.reporterId(), "隐患随手拍 +" + p + " 积分", reason + "，感谢你发现并报告隐患！");
            }
        } else if (e.action() == HazardAction.VERIFY_PASS && e.rectifierId() != null) {
            int p = PointRules.rectifyPoints(e.rectifyTime(), e.deadline());
            String reason = "按期完成隐患 " + e.hazardNo() + "《" + e.title() + "》的整改";
            if (p > 0 && award(e.rectifierId(), e.rectifierName(), p, PointRules.HAZARD_RECTIFY, e.hazardId(), reason, e.operatorName())) {
                notifyUser(e.rectifierId(), "按期整改 +" + p + " 积分", reason);
            }
        }
    }

    /**
     * 加减积分并记流水。同一条来源（sourceId 不为空时）同一个人同一种原因只记一次，重复调用返回 false。
     * 扣减时余额不够抛异常（调用方事务回滚）。
     */
    boolean award(Long userId, String userName, int points, String sourceType, Long sourceId, String reason, String operatorName) {
        if (sourceId != null && recordMapper.selectCount(Wrappers.<PointRecord>lambdaQuery().eq(PointRecord::getUserId, userId)
                .eq(PointRecord::getSourceType, sourceType).eq(PointRecord::getSourceId, sourceId)) > 0) {
            return false;
        }
        PointAccount a = account(userId, userName);
        if (accountMapper.change(a.getId(), points) == 0) {
            throw new BizException("积分余额不够");
        }
        PointRecord r = new PointRecord();
        r.setUserId(userId);
        r.setUserName(userName);
        r.setPoints(points);
        r.setBalanceAfter(accountMapper.selectById(a.getId()).getBalance());
        r.setSourceType(sourceType);
        r.setSourceId(sourceId);
        r.setReason(reason.length() > 200 ? reason.substring(0, 200) : reason);
        r.setOperatorName(operatorName);
        recordMapper.insert(r);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void adjust(PointDTOs.Adjust dto) {
        SysUser target = userService.getById(dto.getUserId());
        PointAccount a = accountOrNull(target.getId());
        String err = PointRules.checkAdjust(dto.getPoints(), dto.getReason(), a == null ? 0 : a.getBalance());
        if (err != null) {
            throw new BizException(err);
        }
        award(target.getId(), target.getNickname(), dto.getPoints(), PointRules.MANUAL, null, dto.getReason().trim(), me().getNickname());
        notifyUser(target.getId(), "积分" + (dto.getPoints() > 0 ? " +" : " ") + dto.getPoints(), dto.getReason().trim());
    }

    // ============================== 查询 ==============================

    public PointVOs.Mine mine() {
        Long me = StpUtil.getLoginIdAsLong();
        PointAccount a = accountOrNull(me);
        LocalDate today = LocalDate.now();
        Map<Long, Integer> month = earned(PointRules.range(PointRules.MONTH, today.getYear(), today.getMonthValue()));
        Map<Long, Integer> year = earned(PointRules.range(PointRules.YEAR, today.getYear(), 1));
        List<PointRecord> records = recordMapper.selectList(Wrappers.<PointRecord>lambdaQuery().eq(PointRecord::getUserId, me)
                .orderByDesc(PointRecord::getCreateTime).orderByDesc(PointRecord::getId).last("LIMIT 30"));
        List<PointRedeem> redeems = redeemMapper.selectList(Wrappers.<PointRedeem>lambdaQuery().eq(PointRedeem::getUserId, me)
                .orderByDesc(PointRedeem::getCreateTime).last("LIMIT 20"));
        return new PointVOs.Mine(a == null ? 0 : a.getBalance(), a == null ? 0 : a.getEarned(), month.getOrDefault(me, 0),
                rankOf(month, me), rankOf(year, me), records, redeems);
    }

    public PointVOs.Ranking ranking(String period, Integer year, Integer month) {
        LocalDate today = LocalDate.now();
        String p = PointRules.YEAR.equals(period) ? PointRules.YEAR : PointRules.MONTH;
        int y = year == null ? today.getYear() : year;
        int m = month == null ? today.getMonthValue() : Math.max(1, Math.min(12, month));
        LocalDate[] range = PointRules.range(p, y, m);
        List<PointRecord> rows = earnRecords(range);
        Map<Long, Integer> points = sum(rows);
        Map<Long, Long> hazards = rows.stream().filter(r -> PointRules.HAZARD_REPORT.equals(r.getSourceType()))
                .collect(Collectors.groupingBy(PointRecord::getUserId, Collectors.counting()));
        Map<Long, UserOptionVO> users = userService.options().stream().collect(Collectors.toMap(UserOptionVO::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> names = rows.stream().collect(Collectors.toMap(PointRecord::getUserId, PointRecord::getUserName, (a, b) -> a));
        List<PointVOs.RankRow> list = PointRules.rank(points).stream().limit(50).map(r -> {
            UserOptionVO u = users.get(r.userId());
            return new PointVOs.RankRow(r.rank(), r.userId(), u != null ? u.getNickname() : names.get(r.userId()), u == null ? null : u.getDeptName(),
                    r.points(), hazards.getOrDefault(r.userId(), 0L).intValue());
        }).toList();
        String label = PointRules.YEAR.equals(p) ? y + " 年" : y + " 年 " + m + " 月";
        return new PointVOs.Ranking(p, label, list, points.values().stream().mapToInt(Integer::intValue).sum(),
                (int) rows.stream().filter(r -> PointRules.HAZARD_REPORT.equals(r.getSourceType())).count());
    }

    public PointVOs.RecordPage records(PointDTOs.RecordQuery q) {
        Page<PointRecord> page = recordMapper.selectPage(Page.of(q.getCurrent(), Math.min(q.getSize(), 100)), Wrappers.<PointRecord>lambdaQuery()
                .eq(q.getUserId() != null, PointRecord::getUserId, q.getUserId())
                .eq(StringUtils.hasText(q.getSourceType()), PointRecord::getSourceType, q.getSourceType())
                .orderByDesc(PointRecord::getCreateTime).orderByDesc(PointRecord::getId));
        return new PointVOs.RecordPage(page.getRecords(), page.getTotal());
    }

    /** 今年的概况（总览卡片、标准化自评“安全文化建设”取证用） */
    public PointVOs.Overview overview(int year) {
        List<PointRecord> rows = earnRecords(PointRules.range(PointRules.YEAR, year, 1));
        List<PointRecord> reports = rows.stream().filter(r -> PointRules.HAZARD_REPORT.equals(r.getSourceType())).toList();
        List<PointRedeem> issued = redeemMapper.selectList(Wrappers.<PointRedeem>lambdaQuery().eq(PointRedeem::getStatus, PointRules.ISSUED)
                .ge(PointRedeem::getHandleTime, LocalDate.of(year, 1, 1).atStartOfDay()).lt(PointRedeem::getHandleTime, LocalDate.of(year + 1, 1, 1).atStartOfDay()));
        long pending = redeemMapper.selectCount(Wrappers.<PointRedeem>lambdaQuery().eq(PointRedeem::getStatus, PointRules.PENDING));
        return new PointVOs.Overview(rows.stream().mapToInt(PointRecord::getPoints).sum(), reports.size(),
                (int) reports.stream().map(PointRecord::getUserId).distinct().count(), (int) pending, issued.size(),
                issued.stream().map(r -> r.getPrice() == null ? BigDecimal.ZERO : r.getPrice()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ============================== 奖品和兑换 ==============================

    public List<PointVOs.RewardRow> rewards(boolean enabledOnly) {
        PointAccount a = accountOrNull(StpUtil.getLoginIdAsLong());
        int balance = a == null ? 0 : a.getBalance();
        return rewardMapper.selectList(Wrappers.<PointReward>lambdaQuery().eq(enabledOnly, PointReward::getStatus, 1)
                        .orderByDesc(PointReward::getStatus).orderByAsc(PointReward::getPoints))
                .stream().map(r -> new PointVOs.RewardRow(r, Integer.valueOf(1).equals(r.getStatus()) && r.getStock() > 0 && balance >= r.getPoints())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveReward(Long id, PointDTOs.RewardSave dto) {
        PointReward r = id == null ? new PointReward() : reward(id);
        r.setName(dto.getName().trim());
        r.setPoints(dto.getPoints());
        r.setPrice(dto.getPrice().setScale(2, java.math.RoundingMode.HALF_UP));
        r.setStock(dto.getStock());
        r.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null);
        if (id == null) {
            r.setStatus(1);
            rewardMapper.insert(r);
        } else {
            rewardMapper.updateById(r);
        }
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rewardStatus(Long id, int status) {
        PointReward r = reward(id);
        r.setStatus(status == 1 ? 1 : 0);
        rewardMapper.updateById(r);
    }

    /** 兑换：先扣库存、扣积分（都是条件更新，并发安全），等管理员发放 */
    @Transactional(rollbackFor = Exception.class)
    public Long redeem(Long rewardId) {
        SysUser me = me();
        PointReward reward = reward(rewardId);
        PointAccount a = accountOrNull(me.getId());
        String err = PointRules.checkRedeem(a == null ? 0 : a.getBalance(), reward.getPoints(), reward.getStock(), Integer.valueOf(1).equals(reward.getStatus()));
        if (err != null) {
            throw new BizException(err);
        }
        if (rewardMapper.changeStock(rewardId, -1) == 0) {
            throw new BizException("奖品已经兑完了");
        }
        PointRedeem r = new PointRedeem();
        r.setRedeemNo(serialNo.next("DH"));
        r.setUserId(me.getId());
        r.setUserName(me.getNickname());
        r.setRewardId(reward.getId());
        r.setRewardName(reward.getName());
        r.setPoints(reward.getPoints());
        r.setPrice(reward.getPrice());
        r.setStatus(PointRules.PENDING);
        redeemMapper.insert(r);
        award(me.getId(), me.getNickname(), -reward.getPoints(), PointRules.REDEEM, r.getId(), "兑换 " + reward.getName() + "（" + r.getRedeemNo() + "）", me.getNickname());
        List<Long> to = messageService.listPermissionUsers(PERM_EDIT).stream().filter(u -> !u.equals(me.getId())).toList();
        messageService.send(to, MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, r.getId(), "积分兑换待发放：" + reward.getName(),
                me.getNickname() + " 用 " + reward.getPoints() + " 积分兑换了 " + reward.getName() + "，请发放", LINK));
        return r.getId();
    }

    public List<PointRedeem> redeems(String status) {
        return redeemMapper.selectList(Wrappers.<PointRedeem>lambdaQuery().eq(StringUtils.hasText(status), PointRedeem::getStatus, status)
                .orderByAsc(PointRedeem::getStatus).orderByDesc(PointRedeem::getCreateTime).last("LIMIT 200"));
    }

    /** 发放（金额记入安全投入）或退回（积分、库存还回去） */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, PointDTOs.Handle dto) {
        PointRedeem r = redeemMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "兑换记录不存在");
        }
        if (!PointRules.PENDING.equals(r.getStatus())) {
            throw new BizException("这条兑换已经处理过了");
        }
        SysUser me = me();
        String remark = StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null;
        if (Boolean.TRUE.equals(dto.getIssue())) {
            if (r.getPrice() != null && r.getPrice().signum() > 0) {
                CostDTOs.ExpenseSave e = new CostDTOs.ExpenseSave();
                e.setCategory("TRAINING");
                e.setTitle("隐患报告奖励：" + r.getUserName() + " 积分兑换 " + r.getRewardName());
                e.setAmount(r.getPrice());
                e.setExpenseDate(LocalDate.now());
                e.setDeptName("安全环保部");
                e.setVoucherNo(r.getRedeemNo());
                e.setRemark("隐患随手拍积分兑换，自动记入");
                r.setCostExpenseId(costService.saveExpense(null, e).id());
            }
            r.setStatus(PointRules.ISSUED);
            notifyUser(r.getUserId(), "兑换的奖品已发放：" + r.getRewardName(), "请到安全环保部领取" + (remark == null ? "" : "（" + remark + "）"));
        } else {
            if (remark == null) {
                throw new BizException("退回要写明原因");
            }
            award(r.getUserId(), r.getUserName(), r.getPoints(), PointRules.REDEEM_RETURN, r.getId(), "兑换退回：" + r.getRewardName() + "（" + remark + "）", me.getNickname());
            rewardMapper.changeStock(r.getRewardId(), 1);
            r.setStatus(PointRules.REJECTED);
            notifyUser(r.getUserId(), "兑换被退回：" + r.getRewardName(), remark + "。" + r.getPoints() + " 积分已退回");
        }
        r.setHandlerName(me.getNickname());
        r.setHandleTime(LocalDateTime.now());
        r.setRemark(remark);
        redeemMapper.updateById(r);
    }

    // ============================== 内部 ==============================

    private PointAccount account(Long userId, String userName) {
        PointAccount a = accountOrNull(userId);
        if (a != null) {
            return a;
        }
        a = new PointAccount();
        a.setUserId(userId);
        a.setUserName(userName);
        a.setBalance(0);
        a.setEarned(0);
        try {
            accountMapper.insert(a);
            return a;
        } catch (DuplicateKeyException e) {
            return accountOrNull(userId);
        }
    }

    private PointAccount accountOrNull(Long userId) {
        return accountMapper.selectOne(Wrappers.<PointAccount>lambdaQuery().eq(PointAccount::getUserId, userId).last("LIMIT 1"));
    }

    private PointReward reward(Long id) {
        PointReward r = rewardMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "奖品不存在");
        }
        return r;
    }

    private List<PointRecord> earnRecords(LocalDate[] range) {
        return recordMapper.selectList(Wrappers.<PointRecord>lambdaQuery().in(PointRecord::getSourceType, EARN_TYPES).gt(PointRecord::getPoints, 0)
                .ge(PointRecord::getCreateTime, range[0].atStartOfDay()).lt(PointRecord::getCreateTime, range[1].atStartOfDay())
                .select(PointRecord::getUserId, PointRecord::getUserName, PointRecord::getPoints, PointRecord::getSourceType));
    }

    private Map<Long, Integer> earned(LocalDate[] range) {
        return sum(earnRecords(range));
    }

    private static Map<Long, Integer> sum(List<PointRecord> rows) {
        Map<Long, Integer> m = new HashMap<>();
        rows.forEach(r -> m.merge(r.getUserId(), r.getPoints(), Integer::sum));
        return m;
    }

    private static Integer rankOf(Map<Long, Integer> points, Long userId) {
        return PointRules.rank(points).stream().filter(r -> r.userId().equals(userId)).map(PointRules.Ranked::rank).findFirst().orElse(null);
    }

    private void notifyUser(Long userId, String title, String content) {
        messageService.send(List.of(userId), MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE, null, title, content, MOBILE_LINK));
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }
}
