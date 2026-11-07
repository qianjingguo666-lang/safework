package com.safework.ticket.visit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 外来人员与车辆入厂的规则（纯计算，方便单元测试）。
 * <pre>
 * 预约（员工替来访人登记）──▶ 访客、普通车辆：直接通过；危化品运输车辆：安全员审批（不能审批自己登记的）
 *      │
 * 门岗（当天）：核对证件 ──▶ 来访人手机上阅读《入厂安全须知》并手写签名 ──▶ 入厂登记 ──▶ 离厂登记
 *      危化品车辆入厂前再检查一遍：驾驶员、押运员的从业资格证，车辆道路运输证都在有效期内，运的货物写清楚
 * 在厂清单：发生事故时按清单清点外来人员；过了预约日期还没离厂的提醒接待人和门岗
 * 身份证号只保存打码后的（前 3 位 + 后 4 位），完整号码只用来校验，不落库
 * </pre>
 */
public final class VisitRules {

    public static final String VISITOR = "VISITOR";
    public static final String VEHICLE = "VEHICLE";
    public static final String HAZMAT = "HAZMAT";
    public static final Map<String, String> TYPES = ordered(VISITOR, "访客", VEHICLE, "普通车辆", HAZMAT, "危化品运输车辆");

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String ENTERED = "ENTERED";
    public static final String LEFT = "LEFT";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";
    public static final Map<String, String> STATUS = ordered(PENDING, "待审批", APPROVED, "待入厂", ENTERED, "在厂", LEFT, "已离厂",
            REJECTED, "已驳回", CANCELLED, "已取消", EXPIRED, "已过期");

    /** 状态动作：from → to */
    public enum Action {
        APPROVE("审批通过", List.of(PENDING), APPROVED),
        REJECT("驳回", List.of(PENDING), REJECTED),
        CANCEL("取消", List.of(PENDING, APPROVED), CANCELLED),
        ENTER("入厂", List.of(APPROVED), ENTERED),
        LEAVE("离厂", List.of(ENTERED), LEFT),
        EXPIRE("过期", List.of(APPROVED, PENDING), EXPIRED);

        private final String label;
        private final List<String> from;
        private final String to;

        Action(String label, List<String> from, String to) {
            this.label = label;
            this.from = from;
            this.to = to;
        }

        public String label() {
            return label;
        }

        public String to() {
            return to;
        }

        public boolean canFire(String status) {
            return from.contains(status);
        }
    }

    /** 入厂安全须知：来访人阅读后手写签名 */
    public static final List<String> NOTICE = List.of(
            "进入厂区必须佩戴访客证，按接待人指定的路线行走，不得进入与来访事由无关的区域",
            "厂区内严禁吸烟，严禁携带火种；进入罐区、装卸区等防爆区域必须关闭手机或使用防爆手机",
            "进入生产区域必须穿戴安全帽等劳动防护用品，不得触碰阀门、按钮等任何设备",
            "车辆在厂内限速 5 km/h，按指定路线行驶、在指定位置停放；危化品车辆装卸时熄火、接好静电接地线，驾驶员和押运员不得离开",
            "听到应急广播或警报，立即听从指挥，按疏散标识撤离到最近的紧急集合点",
            "发现泄漏、着火、有人受伤等异常情况，立即告知接待人或拨打厂内应急电话");

    private static final Pattern PLATE = Pattern.compile(
            "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-HJ-NP-Z][A-HJ-NP-Z0-9]{4,5}[A-HJ-NP-Z0-9挂学警港澳]$");
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECK = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    /** 危化品车辆的资质信息（只用来检查） */
    public record Hazmat(String plateNo, String driverName, String driverCertNo, LocalDate driverCertExpire, String escortName,
                         String escortCertNo, LocalDate escortCertExpire, String transportPermitNo, LocalDate transportPermitExpire,
                         String cargoName, Double cargoQty) {
    }

    private VisitRules() {
    }

    /** 18 位身份证号校验（GB 11643：前 17 位加权求和取模 11 得校验码） */
    public static boolean validIdCard(String id) {
        if (id == null || !id.matches("^\\d{17}[\\dXx]$")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (id.charAt(i) - '0') * ID_WEIGHTS[i];
        }
        return Character.toUpperCase(id.charAt(17)) == ID_CHECK[sum % 11];
    }

    /** 打码：340***********123X */
    public static String maskId(String id) {
        if (id == null || id.length() < 8) {
            return id;
        }
        return id.substring(0, 3) + "*".repeat(id.length() - 7) + id.substring(id.length() - 4).toUpperCase();
    }

    public static boolean validPlate(String plate) {
        return plate != null && PLATE.matcher(plate.trim().toUpperCase()).matches();
    }

    public static boolean validPhone(String phone) {
        return phone != null && PHONE.matcher(phone.trim()).matches();
    }

    /** 新建时的初始状态：危化品车辆要审批，其他直接通过 */
    public static String initialStatus(String type) {
        return HAZMAT.equals(type) ? PENDING : APPROVED;
    }

    /** 危化品车辆入厂检查：返回不符合的项，空列表表示可以入厂 */
    public static List<String> hazmatProblems(Hazmat h, LocalDate day) {
        List<String> p = new ArrayList<>();
        if (!validPlate(h.plateNo())) {
            p.add("车牌号不正确");
        }
        if (blank(h.driverName()) || blank(h.driverCertNo())) {
            p.add("没有填写驾驶员和他的道路危险货物运输从业资格证");
        } else if (h.driverCertExpire() == null || h.driverCertExpire().isBefore(day)) {
            p.add("驾驶员 " + h.driverName() + " 的从业资格证已过期（" + h.driverCertExpire() + "）");
        }
        if (blank(h.escortName()) || blank(h.escortCertNo())) {
            p.add("危化品运输必须有押运员，并持押运员从业资格证");
        } else if (h.escortCertExpire() == null || h.escortCertExpire().isBefore(day)) {
            p.add("押运员 " + h.escortName() + " 的从业资格证已过期（" + h.escortCertExpire() + "）");
        }
        if (!blank(h.driverName()) && h.driverName().equals(h.escortName())) {
            p.add("驾驶员和押运员不能是同一个人");
        }
        if (blank(h.transportPermitNo())) {
            p.add("没有填写车辆的道路运输证");
        } else if (h.transportPermitExpire() == null || h.transportPermitExpire().isBefore(day)) {
            p.add("车辆道路运输证已过期（" + h.transportPermitExpire() + "）");
        }
        if (blank(h.cargoName()) || h.cargoQty() == null || h.cargoQty() <= 0) {
            p.add("没有写清楚运输的危险化学品和数量");
        }
        return p;
    }

    /** 只能在预约当天入厂 */
    public static String checkEnterDay(LocalDate planDate, LocalDate today) {
        if (planDate.isAfter(today)) {
            return "预约的是 " + planDate + "，还没到入厂日期";
        }
        if (planDate.isBefore(today)) {
            return "预约的是 " + planDate + "，已经过期，请重新预约";
        }
        return null;
    }

    /** 新建校验：日期不能是过去，最多提前 30 天 */
    public static String checkPlanDate(LocalDate planDate, LocalDate today) {
        if (planDate.isBefore(today)) {
            return "来访日期不能早于今天";
        }
        if (planDate.isAfter(today.plusDays(30))) {
            return "最多提前 30 天预约";
        }
        return null;
    }

    /** 超时未离厂：在厂，而且已经过了预约日期 */
    public static boolean overstay(String status, LocalDate planDate, LocalDate today) {
        return ENTERED.equals(status) && planDate.isBefore(today);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
