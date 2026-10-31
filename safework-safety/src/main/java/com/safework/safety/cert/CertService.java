package com.safework.safety.cert;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.CertRequirement;
import com.safework.safety.entity.PersonCert;
import com.safework.safety.excel.CertExcelRow;
import com.safework.safety.mapper.CertRequirementMapper;
import com.safework.safety.mapper.PersonCertMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.excel.ExcelHelper;
import com.safework.system.service.DeptService;
import com.safework.system.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员资质：证书台账 + 开票持证校验。
 * <ul>
 *   <li>数据权限：有 cert:edit 的人（安全员）管理全部证书，其他人只能看自己的</li>
 *   <li>开票校验：作业票提交审批、开始作业时调用 {@link #requireQualified}，持证不合格直接拦截</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CertService {

    public static final String PERM_LIST = "cert:list";
    public static final String PERM_EDIT = "cert:edit";
    public static final String BIZ_TYPE = "CERT";

    private final PersonCertMapper certMapper;
    private final CertRequirementMapper requirementMapper;
    private final UserService userService;
    private final DeptService deptService;

    // ============================== 证书台账 ==============================

    public Page<PersonCert> page(CertQueryDTO q) {
        LocalDate today = LocalDate.now();
        String kw = q.getKeyword();
        LambdaQueryWrapper<PersonCert> w = scoped()
                .eq(StringUtils.hasText(q.getCertType()), PersonCert::getCertType, q.getCertType())
                .eq(q.getUserId() != null, PersonCert::getUserId, q.getUserId())
                .and(StringUtils.hasText(kw), x -> x.like(PersonCert::getUserName, kw).or().like(PersonCert::getCertNo, kw));
        stateFilter(w, q.getState(), today);
        // 快过期的排前面，长期有效的排最后
        w.last("ORDER BY valid_until IS NULL, valid_until, id");
        Page<PersonCert> page = certMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), w);
        page.getRecords().forEach(c -> mark(c, today));
        return page;
    }

    public CertOverviewVO overview() {
        LocalDate today = LocalDate.now();
        long total = certMapper.selectCount(scoped());
        long expired = certMapper.selectCount(stateFilter(scoped(), CertRules.EXPIRED, today));
        long expiring = certMapper.selectCount(stateFilter(scoped(), CertRules.EXPIRING, today));
        long holders = certMapper.selectList(scoped().select(PersonCert::getUserId)).stream()
                .map(PersonCert::getUserId).distinct().count();
        return new CertOverviewVO(total, holders, expiring, expired);
    }

    public PersonCert detail(Long id) {
        PersonCert c = load(id);
        if (!canViewAll() && !Objects.equals(c.getUserId(), StpUtil.getLoginIdAsLong())) {
            throw new BizException(404, "证书不存在");
        }
        return mark(c, LocalDate.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long id, CertSaveDTO dto) {
        if (!CertRules.CERT_TYPES.containsKey(dto.getCertType())) {
            throw new BizException("证书类型不正确");
        }
        LocalDate today = LocalDate.now();
        if (dto.getIssueDate().isAfter(today)) {
            throw new BizException("发证日期不能晚于今天");
        }
        if (dto.getExpireDate() != null && !dto.getExpireDate().isAfter(dto.getIssueDate())) {
            throw new BizException("有效期必须晚于发证日期");
        }
        if (dto.getReviewDate() != null && !dto.getReviewDate().isAfter(dto.getIssueDate())) {
            throw new BizException("复审日期必须晚于发证日期");
        }
        // 租户插件保证只能选本企业的人；停用的员工不能再登记证书
        SysUser holder = userService.getById(dto.getUserId());
        if (!Integer.valueOf(1).equals(holder.getStatus())) {
            throw new BizException("【" + holder.getNickname() + "】的账号已停用");
        }
        String certNo = dto.getCertNo().trim();
        boolean duplicated = certMapper.selectCount(Wrappers.<PersonCert>lambdaQuery()
                .eq(PersonCert::getCertType, dto.getCertType())
                .eq(PersonCert::getCertNo, certNo)
                .ne(id != null, PersonCert::getId, id)) > 0;
        if (duplicated) {
            throw new BizException("证书编号【" + certNo + "】已经登记过了");
        }
        PersonCert c = id == null ? new PersonCert() : load(id);
        c.setUserId(holder.getId());
        c.setUserName(holder.getNickname());
        c.setDeptName(deptService.nameOf(holder.getDeptId()));
        c.setCertType(dto.getCertType());
        c.setCertNo(certNo);
        c.setIssuer(dto.getIssuer());
        c.setIssueDate(dto.getIssueDate());
        c.setExpireDate(dto.getExpireDate());
        c.setReviewDate(dto.getReviewDate());
        c.setValidUntil(CertRules.validUntil(dto.getExpireDate(), dto.getReviewDate()));
        c.setRemark(dto.getRemark());
        c.setUpdaterName(userService.getById(StpUtil.getLoginIdAsLong()).getNickname());
        if (id == null) {
            certMapper.insert(c);
        } else {
            // 有效期、复审日期可能清空（改成长期），updateById 默认跳过 null 字段，这里显式更新
            certMapper.updateById(c);
            certMapper.update(null, Wrappers.<PersonCert>lambdaUpdate()
                    .eq(PersonCert::getId, id)
                    .set(PersonCert::getExpireDate, c.getExpireDate())
                    .set(PersonCert::getReviewDate, c.getReviewDate())
                    .set(PersonCert::getValidUntil, c.getValidUntil()));
        }
        return c.getId();
    }

    public void delete(Long id) {
        certMapper.deleteById(load(id).getId());
    }

    public void export(CertQueryDTO q, HttpServletResponse response) {
        q.setCurrent(1);
        q.setSize(5000);
        List<CertExcelRow> rows = page(q).getRecords().stream().map(c -> {
            CertExcelRow r = new CertExcelRow();
            r.setUserName(c.getUserName());
            r.setDeptName(c.getDeptName());
            r.setCertType(CertRules.CERT_TYPES.getOrDefault(c.getCertType(), c.getCertType()));
            r.setCertNo(c.getCertNo());
            r.setIssuer(c.getIssuer());
            r.setIssueDate(String.valueOf(c.getIssueDate()));
            r.setExpireDate(c.getExpireDate() == null ? "长期" : c.getExpireDate().toString());
            r.setReviewDate(c.getReviewDate() == null ? "" : c.getReviewDate().toString());
            r.setState(switch (c.getState()) {
                case CertRules.EXPIRED -> "已过期";
                case CertRules.EXPIRING -> "即将到期（" + c.getDaysLeft() + " 天）";
                default -> "有效";
            });
            r.setRemark(c.getRemark());
            return r;
        }).toList();
        ExcelHelper.write(response, "人员资质台账", CertExcelRow.class, rows);
    }

    // ============================== 开票持证要求 ==============================

    public List<CertRequirement> requirements() {
        return requirementMapper.selectList(Wrappers.<CertRequirement>lambdaQuery()
                .orderByAsc(CertRequirement::getTicketType, CertRequirement::getHolderRole, CertRequirement::getCertType));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveRequirements(RequirementDTO dto) {
        Set<String> seen = new LinkedHashSet<>();
        List<CertRequirement> rows = new ArrayList<>();
        for (RequirementDTO.Item i : dto.getItems()) {
            if (!CertRules.TICKET_TYPES.containsKey(i.getTicketType())
                    || !CertRules.HOLDER_ROLES.containsKey(i.getHolderRole())
                    || !CertRules.CERT_TYPES.containsKey(i.getCertType())) {
                throw new BizException("持证要求不正确：" + i.getTicketType() + " / " + i.getHolderRole() + " / " + i.getCertType());
            }
            if (seen.add(i.getTicketType() + "/" + i.getHolderRole() + "/" + i.getCertType())) {
                CertRequirement r = new CertRequirement();
                r.setTicketType(i.getTicketType());
                r.setHolderRole(i.getHolderRole());
                r.setCertType(i.getCertType());
                rows.add(r);
            }
        }
        requirementMapper.delete(Wrappers.<CertRequirement>lambdaQuery().isNotNull(CertRequirement::getId));
        rows.forEach(requirementMapper::insert);
    }

    // ============================== 开票校验 ==============================

    /**
     * 校验作业负责人和监护人的证书。
     *
     * @param ticketType 作业类型（CONFINED_SPACE / HOT_WORK / HEIGHT）
     * @param workEnd    作业计划结束时间：证书要一直有效到这一天
     * @return 不合格的人和原因；企业没有配置要求或者都合格时返回空列表
     */
    public List<CertProblemVO> check(String ticketType, Long applicantId, Long guardianId, LocalDateTime workEnd) {
        Map<String, List<String>> required = requirementMapper.selectList(Wrappers.<CertRequirement>lambdaQuery()
                        .eq(CertRequirement::getTicketType, ticketType))
                .stream().collect(Collectors.groupingBy(CertRequirement::getHolderRole,
                        Collectors.mapping(CertRequirement::getCertType, Collectors.toList())));
        List<CertProblemVO> result = new ArrayList<>();
        LocalDate end = workEnd == null ? null : workEnd.toLocalDate();
        checkOne(result, CertRules.APPLICANT, applicantId, required.get(CertRules.APPLICANT), end);
        checkOne(result, CertRules.GUARDIAN, guardianId, required.get(CertRules.GUARDIAN), end);
        return result;
    }

    /** 不合格直接拦截，错误信息写清楚是谁、缺什么 */
    public void requireQualified(String ticketType, Long applicantId, Long guardianId, LocalDateTime workEnd) {
        List<CertProblemVO> problems = check(ticketType, applicantId, guardianId, workEnd);
        if (!problems.isEmpty()) {
            throw new BizException("持证不符合要求：" + problems.stream()
                    .map(p -> p.roleName() + p.userName() + String.join("、", p.problems()))
                    .collect(Collectors.joining("；")) + "。请先更新人员资质，或更换人员");
        }
    }

    private void checkOne(List<CertProblemVO> result, String role, Long userId, List<String> required, LocalDate workEnd) {
        if (userId == null || required == null || required.isEmpty()) {
            return;
        }
        // 这里查的是别人的证书，不走 scoped() 的数据权限；租户隔离仍然由插件保证
        List<CertRules.Held> held = certMapper.selectList(Wrappers.<PersonCert>lambdaQuery()
                        .eq(PersonCert::getUserId, userId)
                        .in(PersonCert::getCertType, required))
                .stream().map(c -> new CertRules.Held(c.getCertType(), c.getCertNo(), c.getExpireDate(), c.getReviewDate()))
                .toList();
        List<String> problems = CertRules.problems(required, held, workEnd, LocalDate.now());
        if (!problems.isEmpty()) {
            result.add(new CertProblemVO(role, CertRules.HOLDER_ROLES.get(role), userService.getById(userId).getNickname(), problems));
        }
    }

    // ============================== 内部方法 ==============================

    /** 数据权限：安全员看全部，其他人只看自己的 */
    private LambdaQueryWrapper<PersonCert> scoped() {
        return Wrappers.<PersonCert>lambdaQuery()
                .eq(!canViewAll(), PersonCert::getUserId, StpUtil.getLoginIdAsLong());
    }

    private static boolean canViewAll() {
        return StpUtil.hasPermission(PERM_EDIT);
    }

    static LambdaQueryWrapper<PersonCert> stateFilter(LambdaQueryWrapper<PersonCert> w, String state, LocalDate today) {
        if (CertRules.EXPIRED.equals(state)) {
            w.lt(PersonCert::getValidUntil, today);
        } else if (CertRules.EXPIRING.equals(state)) {
            w.between(PersonCert::getValidUntil, today, today.plusDays(CertRules.WARN_DAYS));
        } else if (CertRules.VALID.equals(state)) {
            w.and(x -> x.isNull(PersonCert::getValidUntil).or().gt(PersonCert::getValidUntil, today.plusDays(CertRules.WARN_DAYS)));
        }
        return w;
    }

    static PersonCert mark(PersonCert c, LocalDate today) {
        c.setState(CertRules.state(c.getValidUntil(), today));
        c.setDaysLeft(c.getValidUntil() == null ? null : ChronoUnit.DAYS.between(today, c.getValidUntil()));
        return c;
    }

    private PersonCert load(Long id) {
        PersonCert c = certMapper.selectById(id);
        if (c == null) {
            throw new BizException(404, "证书不存在");
        }
        return c;
    }
}
