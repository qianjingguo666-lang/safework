package com.safework.safety.cert;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 人员资质规则（纯函数，方便单元测试）：
 * <ul>
 *   <li>证书状态：有效期、复审日期哪个先到按哪个算；30 天内到期算“即将到期”</li>
 *   <li>开票校验：作业负责人、监护人必须持有要求的证书，并且证书在作业结束前一直有效</li>
 *   <li>到期提醒：剩 30 天、7 天和到期当天各提醒一次，不天天打扰</li>
 * </ul>
 */
public final class CertRules {

    /** 证书类型。特种作业操作证有效期 6 年、每 3 年复审一次 */
    public static final Map<String, String> CERT_TYPES = new LinkedHashMap<>();

    static {
        CERT_TYPES.put("CONFINED_SPACE", "有限空间作业安全培训合格证");
        CERT_TYPES.put("GUARDIAN", "特殊作业监护人培训合格证");
        CERT_TYPES.put("WELDING", "特种作业操作证（焊接与热切割）");
        CERT_TYPES.put("HEIGHT", "特种作业操作证（高处作业）");
        CERT_TYPES.put("ELECTRICIAN", "特种作业操作证（电工）");
        CERT_TYPES.put("HAZCHEM", "特种作业操作证（危险化学品）");
        CERT_TYPES.put("SAFETY_MANAGER", "安全生产管理人员安全资格证");
        CERT_TYPES.put("OTHER", "其他");
    }

    /** 作业票里的两个角色 */
    public static final String APPLICANT = "APPLICANT";
    public static final String GUARDIAN = "GUARDIAN";
    /** 承包商作业人员（不是系统用户，证书登记在承包商人员信息里） */
    public static final String WORKER = "WORKER";
    public static final Map<String, String> HOLDER_ROLES = Map.of(APPLICANT, "作业负责人", GUARDIAN, "监护人", WORKER, "承包商作业人员");
    /** 作业票类型（和 ticket 模块的 TicketType 一致，这里只用字符串，避免安全模块反过来依赖作业票模块） */
    public static final Map<String, String> TICKET_TYPES = new LinkedHashMap<>();

    static {
        TICKET_TYPES.put("CONFINED_SPACE", "受限空间作业");
        TICKET_TYPES.put("HOT_WORK", "动火作业");
        TICKET_TYPES.put("HEIGHT", "高处作业");
    }

    public static final String VALID = "VALID";
    public static final String EXPIRING = "EXPIRING";
    public static final String EXPIRED = "EXPIRED";
    /** 提前多少天算“即将到期” */
    public static final int WARN_DAYS = 30;
    /** 剩这么多天时提醒 */
    public static final Set<Long> REMIND_DAYS = Set.of(30L, 7L, 0L);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private CertRules() {
    }

    /** 证书在这一天之后就不能用了：有效期和复审日期取早的；都没填表示长期有效（返回 null） */
    public static LocalDate validUntil(LocalDate expireDate, LocalDate reviewDate) {
        if (expireDate == null) {
            return reviewDate;
        }
        if (reviewDate == null) {
            return expireDate;
        }
        return expireDate.isBefore(reviewDate) ? expireDate : reviewDate;
    }

    public static String state(LocalDate validUntil, LocalDate today) {
        if (validUntil == null) {
            return VALID;
        }
        if (validUntil.isBefore(today)) {
            return EXPIRED;
        }
        return ChronoUnit.DAYS.between(today, validUntil) <= WARN_DAYS ? EXPIRING : VALID;
    }

    /** 到期原因：复审日期先到说的是“复审”，否则是“有效期” */
    public static String limitName(LocalDate expireDate, LocalDate reviewDate) {
        return reviewDate != null && (expireDate == null || reviewDate.isBefore(expireDate)) ? "复审" : "有效期";
    }

    /** 今天要不要提醒：剩 30、7 天和到期当天 */
    public static boolean shouldRemind(LocalDate validUntil, LocalDate today) {
        return validUntil != null && REMIND_DAYS.contains(ChronoUnit.DAYS.between(today, validUntil));
    }

    /** 某人持有的一张证书（校验用的最小信息） */
    public record Held(String certType, String certNo, LocalDate expireDate, LocalDate reviewDate) {
        LocalDate until() {
            return validUntil(expireDate, reviewDate);
        }
    }

    /**
     * 检查一个人能不能担任作业票里的这个角色。
     *
     * @param required 要求的证书类型
     * @param held     这个人的全部证书
     * @param workEnd  作业结束日期：证书要一直有效到这一天
     * @param today    今天
     * @return 问题列表，空表示合格
     */
    public static List<String> problems(Collection<String> required, List<Held> held, LocalDate workEnd, LocalDate today) {
        List<String> result = new ArrayList<>();
        for (String type : required) {
            String label = CERT_TYPES.getOrDefault(type, type);
            // 同一类证书有多张（比如换证后旧证没删）时，取能用得最久的那张
            Held best = held.stream().filter(h -> Objects.equals(h.certType(), type))
                    .max(Comparator.comparing(Held::until, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            if (best == null) {
                result.add("没有【" + label + "】");
                continue;
            }
            LocalDate until = best.until();
            if (until == null) {
                continue;
            }
            String what = limitName(best.expireDate(), best.reviewDate());
            if (until.isBefore(today)) {
                result.add("【" + label + "】" + ("复审".equals(what) ? "复审已逾期" : "已过期") + "（" + until.format(DATE) + "）");
            } else if (workEnd != null && until.isBefore(workEnd)) {
                result.add("【" + label + "】" + what + "到 " + until.format(DATE) + "，早于作业结束时间");
            }
        }
        return result;
    }
}
