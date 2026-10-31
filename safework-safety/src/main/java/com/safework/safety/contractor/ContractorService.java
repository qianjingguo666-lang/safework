package com.safework.safety.contractor;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.cert.CertProblemVO;
import com.safework.safety.cert.CertRules;
import com.safework.safety.cert.CertService;
import com.safework.safety.entity.Contractor;
import com.safework.safety.entity.ContractorViolation;
import com.safework.safety.entity.ContractorWorker;
import com.safework.safety.mapper.ContractorMapper;
import com.safework.safety.mapper.ContractorViolationMapper;
import com.safework.safety.mapper.ContractorWorkerMapper;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 承包商管理：准入审核、人员登记（入厂培训、特种作业证）、违章扣分和黑名单，以及作业票的进场校验。
 * <p>
 * 作业票选了承包商时，提交审批和开始作业都会调用 {@link #requireEligible}：
 * 单位必须审核合格、评分不低于 60、资质和保险覆盖作业期间；每个作业人员入厂培训有效，
 * 并且持有【人员资质 → 开票持证要求】里“承包商作业人员”要求的证书。
 */
@Service
@RequiredArgsConstructor
public class ContractorService {

    public static final String PERM_LIST = "contractor:list";
    public static final String PERM_EDIT = "contractor:edit";
    static final String BIZ_TYPE = "CONTRACTOR";
    static final String LINK = "/safety/contractor";
    /** 一张作业票最多带多少个承包商人员 */
    static final int MAX_WORKERS = 20;

    private final ContractorMapper contractorMapper;
    private final ContractorWorkerMapper workerMapper;
    private final ContractorViolationMapper violationMapper;
    private final CertService certService;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 查询 ==============================

    public Page<Contractor> page(ContractorQueryDTO q) {
        String kw = q.getKeyword();
        Page<Contractor> page = contractorMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), Wrappers.<Contractor>lambdaQuery()
                .eq(StringUtils.hasText(q.getStatus()), Contractor::getStatus, q.getStatus())
                .and(StringUtils.hasText(kw), w -> w.like(Contractor::getName, kw).or().like(Contractor::getCreditCode, kw))
                .orderByDesc(Contractor::getCreateTime));
        enrich(page.getRecords());
        return page;
    }

    public ContractorOverviewVO overview() {
        LocalDate today = LocalDate.now();
        Map<String, Long> byStatus = contractorMapper.selectList(Wrappers.<Contractor>lambdaQuery()
                        .select(Contractor::getStatus))
                .stream().collect(Collectors.groupingBy(Contractor::getStatus, Collectors.counting()));
        long expiring = contractorMapper.selectCount(Wrappers.<Contractor>lambdaQuery()
                .eq(Contractor::getStatus, ContractorRules.APPROVED)
                .and(w -> w.le(Contractor::getLicenseExpire, today.plusDays(ContractorRules.WARN_DAYS))
                        .or().le(Contractor::getInsuranceExpire, today.plusDays(ContractorRules.WARN_DAYS))));
        // 培训日期早于 (今天 + 30 天 - 12 个月) 的，30 天内培训就过期了
        long workerWarnings = workerMapper.selectCount(Wrappers.<ContractorWorker>lambdaQuery()
                .eq(ContractorWorker::getStatus, 1)
                .and(w -> w.isNull(ContractorWorker::getTrainingDate).or()
                        .le(ContractorWorker::getTrainingDate, today.plusDays(ContractorRules.WARN_DAYS).minusMonths(ContractorRules.TRAINING_MONTHS))));
        return new ContractorOverviewVO(byStatus.getOrDefault(ContractorRules.APPROVED, 0L), byStatus.getOrDefault(ContractorRules.PENDING, 0L),
                byStatus.getOrDefault(ContractorRules.BLACKLISTED, 0L), expiring, workerWarnings);
    }

    public ContractorDetailVO detail(Long id) {
        Contractor c = load(id);
        enrich(List.of(c));
        LocalDate today = LocalDate.now();
        List<ContractorWorker> workers = workerMapper.selectList(Wrappers.<ContractorWorker>lambdaQuery()
                .eq(ContractorWorker::getContractorId, id)
                .orderByDesc(ContractorWorker::getStatus)
                .orderByAsc(ContractorWorker::getId));
        workers.forEach(w -> markWorker(w, today));
        List<ContractorViolation> violations = violationMapper.selectList(Wrappers.<ContractorViolation>lambdaQuery()
                .eq(ContractorViolation::getContractorId, id)
                .orderByDesc(ContractorViolation::getOccurTime));
        List<String> actions = new ArrayList<>();
        if (StpUtil.hasPermission(PERM_EDIT)) {
            for (ContractorRules.Action a : ContractorRules.Action.values()) {
                if (a.allowedFrom(c.getStatus())) {
                    actions.add(a.name());
                }
            }
        }
        return new ContractorDetailVO(c, workers, violations, actions);
    }

    /** 新建作业票时的下拉：只列审核合格的单位和在场人员（具体能不能作业由 ticket-check 给出原因） */
    public List<ContractorOptionVO> options() {
        List<Contractor> list = contractorMapper.selectList(Wrappers.<Contractor>lambdaQuery()
                .eq(Contractor::getStatus, ContractorRules.APPROVED)
                .orderByAsc(Contractor::getName));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ContractorWorker>> workers = workerMapper.selectList(Wrappers.<ContractorWorker>lambdaQuery()
                        .in(ContractorWorker::getContractorId, list.stream().map(Contractor::getId).toList())
                        .eq(ContractorWorker::getStatus, 1)
                        .orderByAsc(ContractorWorker::getId))
                .stream().collect(Collectors.groupingBy(ContractorWorker::getContractorId));
        return list.stream().map(c -> new ContractorOptionVO(c.getId(), c.getName(),
                workers.getOrDefault(c.getId(), List.of()).stream()
                        .map(w -> new ContractorOptionVO.Worker(w.getId(), w.getName(), w.getJobType())).toList())).toList();
    }

    // ============================== 单位 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long id, ContractorSaveDTO dto) {
        String name = dto.getName().trim();
        if (contractorMapper.selectCount(Wrappers.<Contractor>lambdaQuery()
                .and(w -> w.eq(Contractor::getName, name).or().eq(Contractor::getCreditCode, dto.getCreditCode()))
                .ne(id != null, Contractor::getId, id)) > 0) {
            throw new BizException("单位名称或统一社会信用代码已经登记过了");
        }
        Contractor c = id == null ? new Contractor() : load(id);
        c.setName(name);
        c.setCreditCode(dto.getCreditCode());
        c.setContactName(dto.getContactName().trim());
        c.setContactPhone(dto.getContactPhone());
        c.setBusinessScope(dto.getBusinessScope());
        c.setLicenseExpire(dto.getLicenseExpire());
        c.setInsuranceExpire(dto.getInsuranceExpire());
        c.setRemark(dto.getRemark());
        if (id == null) {
            // 新登记的单位要审核后才能作业
            c.setStatus(ContractorRules.PENDING);
            contractorMapper.insert(c);
        } else {
            contractorMapper.updateById(c);
        }
        return c.getId();
    }

    /** 审核通过 / 驳回 / 重新提交 / 拉黑 / 解除黑名单 */
    @Transactional(rollbackFor = Exception.class)
    public void act(Long id, ContractorRules.Action action, ContractorActionDTO dto) {
        Contractor c = load(id);
        if (!action.allowedFrom(c.getStatus())) {
            throw new BizException("当前状态是【" + ContractorRules.STATUS_LABELS.get(c.getStatus()) + "】，不能" + action.label);
        }
        String comment = dto == null ? null : dto.getComment();
        boolean needComment = action == ContractorRules.Action.REJECT || action == ContractorRules.Action.BLACKLIST
                || action == ContractorRules.Action.LIFT;
        if (needComment && !StringUtils.hasText(comment)) {
            throw new BizException("请填写" + action.label + "的原因");
        }
        c.setStatus(action.to);
        c.setStatusComment(StringUtils.hasText(comment) ? comment.trim() : action.label);
        c.setReviewerName(userService.getById(StpUtil.getLoginIdAsLong()).getNickname());
        c.setReviewTime(LocalDateTime.now());
        contractorMapper.updateById(c);
    }

    // ============================== 人员 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long saveWorker(Long contractorId, Long workerId, WorkerSaveDTO dto) {
        Contractor c = load(contractorId);
        if (ContractorRules.BLACKLISTED.equals(c.getStatus())) {
            throw new BizException("黑名单单位不能登记人员");
        }
        boolean hasCert = StringUtils.hasText(dto.getCertType());
        if (hasCert) {
            if (!CertRules.CERT_TYPES.containsKey(dto.getCertType())) {
                throw new BizException("证书类型不正确");
            }
            if (!StringUtils.hasText(dto.getCertNo()) || dto.getCertExpire() == null) {
                throw new BizException("登记特种作业证要同时填写证书编号和有效期");
            }
        }
        if (dto.getTrainingDate() != null && dto.getTrainingDate().isAfter(LocalDate.now())) {
            throw new BizException("培训日期不能晚于今天");
        }
        ContractorWorker w = workerId == null ? new ContractorWorker() : loadWorker(contractorId, workerId);
        w.setContractorId(contractorId);
        w.setName(dto.getName().trim());
        w.setPhone(StringUtils.hasText(dto.getPhone()) ? dto.getPhone() : null);
        w.setJobType(dto.getJobType());
        w.setTrainingDate(dto.getTrainingDate());
        w.setCertType(hasCert ? dto.getCertType() : null);
        w.setCertNo(hasCert ? dto.getCertNo().trim() : null);
        w.setCertExpire(hasCert ? dto.getCertExpire() : null);
        if (workerId == null) {
            w.setStatus(1);
            workerMapper.insert(w);
        } else {
            workerMapper.updateById(w);
        }
        return w.getId();
    }

    /** 离场 / 重新进场 */
    public void changeWorkerStatus(Long contractorId, Long workerId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态不正确");
        }
        ContractorWorker w = loadWorker(contractorId, workerId);
        w.setStatus(status);
        workerMapper.updateById(w);
    }

    // ============================== 违章 ==============================

    /** 记录违章并扣分；合格单位评分低于 60 分自动列入黑名单，通知安全员 */
    @Transactional(rollbackFor = Exception.class)
    public ViolationResultVO addViolation(Long contractorId, ViolationSaveDTO dto) {
        Contractor c = load(contractorId);
        if (dto.getOccurTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BizException("违章时间不能晚于现在");
        }
        ContractorViolation v = new ContractorViolation();
        v.setContractorId(contractorId);
        if (dto.getWorkerId() != null) {
            ContractorWorker w = loadWorker(contractorId, dto.getWorkerId());
            v.setWorkerId(w.getId());
            v.setWorkerName(w.getName());
        }
        v.setDescription(dto.getDescription().trim());
        v.setPoints(dto.getPoints());
        v.setOccurTime(dto.getOccurTime());
        String me = userService.getById(StpUtil.getLoginIdAsLong()).getNickname();
        v.setRecorderName(me);
        violationMapper.insert(v);

        int score = ContractorRules.score(pointsOf(List.of(contractorId)).getOrDefault(contractorId, 0));
        boolean blacklisted = false;
        if (ContractorRules.APPROVED.equals(c.getStatus()) && ContractorRules.shouldBlacklist(score)) {
            c.setStatus(ContractorRules.BLACKLISTED);
            c.setStatusComment("近 12 个月安全评分 " + score + " 分，低于 " + ContractorRules.BLACKLIST_LINE + " 分，自动列入黑名单");
            c.setReviewerName("系统");
            c.setReviewTime(LocalDateTime.now());
            contractorMapper.updateById(c);
            blacklisted = true;
            messageService.sendToPermission(PERM_EDIT, null, MessageDraft.of(MessageCategory.WARN, BIZ_TYPE, c.getId(),
                    "承包商列入黑名单：" + c.getName(),
                    "最近一次违章：" + v.getDescription() + "（扣 " + v.getPoints() + " 分，" + me + " 记录）。近 12 个月安全评分 " + score
                            + " 分，已自动列入黑名单，该单位人员不能再参与作业", LINK));
        }
        return new ViolationResultVO(score, blacklisted);
    }

    // ============================== 作业票校验 ==============================

    /** 作业票保存时记录的承包商信息 */
    public record Snapshot(Long contractorId, String contractorName, String workerIds, String workerNames) {
    }

    /** 作业票保存草稿时：检查人员属于这个承包商，生成快照（名字存进作业票，以后改名不影响历史票） */
    public Snapshot snapshot(Long contractorId, List<Long> workerIds) {
        if (contractorId == null) {
            return new Snapshot(null, null, null, null);
        }
        Contractor c = load(contractorId);
        List<Long> ids = workerIds == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(workerIds));
        if (ids.isEmpty()) {
            throw new BizException("请选择承包商的作业人员");
        }
        if (ids.size() > MAX_WORKERS) {
            throw new BizException("一张作业票最多 " + MAX_WORKERS + " 名承包商人员");
        }
        List<ContractorWorker> workers = ids.stream().map(wid -> loadWorker(contractorId, wid)).toList();
        return new Snapshot(c.getId(), c.getName(),
                workers.stream().map(w -> String.valueOf(w.getId())).collect(Collectors.joining(",")),
                workers.stream().map(ContractorWorker::getName).collect(Collectors.joining("、")));
    }

    /**
     * 检查承包商和作业人员能不能参与这张作业票
     *
     * @return 不合格的单位 / 人员和原因，空表示可以
     */
    public List<CertProblemVO> check(String ticketType, Long contractorId, Collection<Long> workerIds, LocalDateTime workEnd) {
        List<CertProblemVO> result = new ArrayList<>();
        if (contractorId == null) {
            return result;
        }
        LocalDate today = LocalDate.now();
        LocalDate end = workEnd == null ? null : workEnd.toLocalDate();
        Contractor c = load(contractorId);
        int score = ContractorRules.score(pointsOf(List.of(contractorId)).getOrDefault(contractorId, 0));
        List<String> cp = ContractorRules.contractorProblems(c.getStatus(), score, c.getLicenseExpire(), c.getInsuranceExpire(), end, today);
        if (!cp.isEmpty()) {
            result.add(new CertProblemVO("CONTRACTOR", "承包商", c.getName(), cp));
        }
        List<String> required = certService.requiredTypes(ticketType, CertRules.WORKER);
        Collection<Long> distinct = workerIds == null ? List.of() : new LinkedHashSet<Long>(workerIds);
        for (Long wid : distinct) {
            ContractorWorker w = loadWorker(contractorId, wid);
            List<String> wp = new ArrayList<>(ContractorRules.workerProblems(Objects.equals(w.getStatus(), 1), w.getTrainingDate(), end, today));
            if (wp.isEmpty() && !required.isEmpty()) {
                List<CertRules.Held> held = w.getCertType() == null ? List.of()
                        : List.of(new CertRules.Held(w.getCertType(), w.getCertNo(), w.getCertExpire(), null));
                wp.addAll(CertRules.problems(required, held, end, today));
            }
            if (!wp.isEmpty()) {
                result.add(new CertProblemVO(CertRules.WORKER, CertRules.HOLDER_ROLES.get(CertRules.WORKER), w.getName(), wp));
            }
        }
        return result;
    }

    public void requireEligible(String ticketType, Long contractorId, Collection<Long> workerIds, LocalDateTime workEnd) {
        List<CertProblemVO> problems = check(ticketType, contractorId, workerIds, workEnd);
        if (!problems.isEmpty()) {
            throw new BizException("承包商不符合进场要求：" + problems.stream()
                    .map(p -> p.roleName() + p.userName() + String.join("、", p.problems()))
                    .collect(Collectors.joining("；")));
        }
    }

    /** 作业票里存的逗号分隔 ID → 列表 */
    public static List<Long> parseIds(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            if (StringUtils.hasText(s)) {
                ids.add(Long.valueOf(s.trim()));
            }
        }
        return ids;
    }

    // ============================== 内部方法 ==============================

    /** 近 12 个月的扣分合计，按承包商分组 */
    private Map<Long, Integer> pointsOf(List<Long> contractorIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (contractorIds.isEmpty()) {
            return result;
        }
        QueryWrapper<ContractorViolation> w = new QueryWrapper<ContractorViolation>()
                .select("contractor_id AS cid", "SUM(points) AS pts")
                .in("contractor_id", contractorIds)
                .ge("occur_time", LocalDateTime.now().minusMonths(ContractorRules.SCORE_MONTHS))
                .groupBy("contractor_id");
        for (Map<String, Object> row : violationMapper.selectMaps(w)) {
            Object cid = row.get("cid");
            Object pts = row.get("pts");
            if (cid != null && pts != null) {
                result.put(((Number) cid).longValue(), ((Number) pts).intValue());
            }
        }
        return result;
    }

    private void enrich(List<Contractor> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(Contractor::getId).toList();
        Map<Long, Integer> points = pointsOf(ids);
        Map<Long, Long> counts = workerMapper.selectList(Wrappers.<ContractorWorker>lambdaQuery()
                        .select(ContractorWorker::getContractorId)
                        .in(ContractorWorker::getContractorId, ids)
                        .eq(ContractorWorker::getStatus, 1))
                .stream().collect(Collectors.groupingBy(ContractorWorker::getContractorId, Collectors.counting()));
        LocalDate today = LocalDate.now();
        for (Contractor c : list) {
            c.setScore(ContractorRules.score(points.getOrDefault(c.getId(), 0)));
            c.setWorkerCount(counts.getOrDefault(c.getId(), 0L));
            List<String> warnings = new ArrayList<>();
            if (ContractorRules.APPROVED.equals(c.getStatus())) {
                warn(warnings, "资质证书", c.getLicenseExpire(), today);
                warn(warnings, "安全生产责任险", c.getInsuranceExpire(), today);
            }
            c.setWarnings(warnings);
        }
    }

    private static void warn(List<String> out, String what, LocalDate expire, LocalDate today) {
        if (expire == null) {
            return;
        }
        if (expire.isBefore(today)) {
            out.add(what + "已过期");
        } else if (ContractorRules.expiringSoon(expire, today)) {
            out.add(what + " " + java.time.temporal.ChronoUnit.DAYS.between(today, expire) + " 天后到期");
        }
    }

    private static void markWorker(ContractorWorker w, LocalDate today) {
        w.setTrainingExpire(ContractorRules.trainingExpire(w.getTrainingDate()));
        List<String> p = new ArrayList<>(ContractorRules.workerProblems(Objects.equals(w.getStatus(), 1), w.getTrainingDate(), null, today));
        if (w.getCertExpire() != null && w.getCertExpire().isBefore(today)) {
            p.add("特种作业证已过期");
        }
        if (p.isEmpty() && ContractorRules.expiringSoon(w.getTrainingExpire(), today)) {
            p.add("入厂安全培训 " + w.getTrainingExpire() + " 到期，请安排复训");
        }
        w.setProblems(p);
    }

    private Contractor load(Long id) {
        Contractor c = contractorMapper.selectById(id);
        if (c == null) {
            throw new BizException(404, "承包商不存在");
        }
        return c;
    }

    private ContractorWorker loadWorker(Long contractorId, Long workerId) {
        ContractorWorker w = workerMapper.selectById(workerId);
        if (w == null || !Objects.equals(w.getContractorId(), contractorId)) {
            throw new BizException(404, "承包商人员不存在");
        }
        return w;
    }
}
