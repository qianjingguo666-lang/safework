package com.safework.ai.core.hazard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 隐患提取的规则部分（纯计算，方便单元测试）：
 * 1. 没有配置大模型、或者大模型出错时，按关键词粗略提取（兜底）
 * 2. 大模型的结果也要经过这里校验、纠正（类别、等级只能是固定的值，受限空间必须真实存在）
 */
public final class HazardRules {

    public static final List<String> CATEGORIES = List.of("设备设施", "作业环境", "人员行为", "安全管理");
    public static final String GENERAL = "GENERAL";
    public static final String MAJOR = "MAJOR";
    public static final int MAX_TITLE = 40;
    public static final int MAX_LOCATION = 100;
    public static final int MAX_DESCRIPTION = 1000;

    /** 出现这些词，按重大隐患处理（宁可报高，由安全员核实后调整） */
    private static final List<String> MAJOR_WORDS = List.of("泄漏", "泄露", "漏气", "爆炸", "着火", "起火", "火苗", "中毒", "窒息", "晕倒", "昏倒",
            "坍塌", "倒塌", "断裂", "无票", "没有作业票", "没办作业票", "没有检测", "没检测", "未检测", "失灵", "失效", "不报警", "报警器不亮", "报警器不响", "报警器坏", "重大");

    private static final Map<String, List<String>> CATEGORY_WORDS = new LinkedHashMap<>();

    static {
        CATEGORY_WORDS.put("人员行为", List.of("没戴", "未戴", "不戴", "没系", "未系", "不系", "没穿", "未穿", "抽烟", "吸烟", "打电话", "玩手机",
                "睡觉", "脱岗", "离岗", "违章", "违规", "酒后", "攀爬", "跨越", "冒险"));
        CATEGORY_WORDS.put("设备设施", List.of("阀门", "管道", "管线", "法兰", "泵", "电机", "设备", "仪表", "报警器", "探头", "压力表", "安全阀",
                "灭火器", "消防栓", "护栏", "栏杆", "接地", "电线", "电缆", "配电", "开关", "锈蚀", "损坏", "破损", "泄漏", "泄露", "渗漏", "漏油", "漏气"));
        CATEGORY_WORDS.put("作业环境", List.of("积水", "油污", "杂物", "堆放", "通道", "堵塞", "照明", "太暗", "地面", "湿滑", "警示", "标识",
                "通风", "异味", "气味", "粉尘", "噪声", "高温", "坑", "洞口", "孔洞"));
        CATEGORY_WORDS.put("安全管理", List.of("台账", "记录", "制度", "培训", "交底", "无票", "作业票", "检测记录", "过期", "超期", "未检验",
                "没检验", "未审批", "没审批", "证书", "持证", "应急预案"));
    }

    /** 位置：“在 X 发现……”、“X 旁边/附近/里面……” */
    private static final Pattern LOCATION_AFTER_ZAI = Pattern.compile("(?<![正现还存实所])在([^，。,；;！!？?\\s]{2,20}?)(?:发现|看到|有|的|旁边|附近|里面|里|上面|下面|门口|处|，|。|,)");
    private static final Pattern LOCATION_PLACE = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9#＃号\\-]{1,12}?(?:车间|罐区|储罐|仓库|库房|厂房|泵房|机房|配电室|控制室|楼|层|区|站|路|沟|池|釜|塔|罐|门口|平台))");
    private static final Pattern SENTENCE = Pattern.compile("[。！？!?；;\\n]");
    private static final String CN_DIGITS = "零一二三四五六七八九";

    private HazardRules() {
    }

    // ============================== 兜底：关键词提取 ==============================

    public static HazardDraft extract(String text, List<SpaceRef> spaces) {
        String t = clean(text);
        SpaceRef space = matchSpace(t, spaces);
        String majorWord = MAJOR_WORDS.stream().filter(t::contains).findFirst().orElse(null);
        String location = space != null ? spaceLocation(space) : guessLocation(t);
        return new HazardDraft(guessTitle(t), guessCategory(t), majorWord == null ? GENERAL : MAJOR,
                location, space == null ? null : space.code(), truncate(t, MAX_DESCRIPTION),
                majorWord == null ? null : "描述中提到“" + majorWord + "”");
    }

    public static String guessCategory(String text) {
        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, List<String>> e : CATEGORY_WORDS.entrySet()) {
            int score = (int) e.getValue().stream().filter(text::contains).count();
            if (score > bestScore) {
                best = e.getKey();
                bestScore = score;
            }
        }
        return best == null ? "作业环境" : best;
    }

    public static String guessTitle(String text) {
        String first = SENTENCE.split(text, 2)[0].trim();
        // 去掉口头语开头
        first = first.replaceFirst("^(那个|就是|嗯|呃|我发现|发现|报告一下|报一个隐患)[，,]?", "").trim();
        if (first.isEmpty()) {
            first = text.trim();
        }
        return truncate(first, MAX_TITLE);
    }

    public static String guessLocation(String text) {
        Matcher m = LOCATION_AFTER_ZAI.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        m = LOCATION_PLACE.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    // ============================== 受限空间匹配 ==============================

    /**
     * 根据说的话匹配受限空间：编号（CS-001）、位号（T-101）、名称（原料储罐）都可以。
     * 匹配到多个分数一样的就不猜，返回 null，避免关联错。
     */
    public static SpaceRef matchSpace(String text, List<SpaceRef> spaces) {
        if (text == null || spaces == null || spaces.isEmpty()) {
            return null;
        }
        String t = normalize(text);
        SpaceRef best = null;
        int bestScore = 0;
        boolean tie = false;
        for (SpaceRef s : spaces) {
            int score = score(t, s);
            if (score > bestScore) {
                best = s;
                bestScore = score;
                tie = false;
            } else if (score == bestScore && score > 0) {
                tie = true;
            }
        }
        return bestScore >= 60 && !tie ? best : null;
    }

    private static int score(String t, SpaceRef s) {
        String name = normalize(s.name());
        if (!name.isEmpty() && t.contains(name)) {
            return 100;
        }
        int score = 0;
        if (s.code() != null && t.contains(normalize(s.code()))) {
            score += 90;
        }
        String number = null;
        for (String token : s.name().trim().split("\\s+")) {
            String n = normalize(token);
            if (n.isEmpty()) {
                continue;
            }
            if (n.matches("\\d+")) {
                number = n;
            } else if (n.matches(".*[a-z].*\\d.*|.*\\d.*[a-z].*") && n.length() >= 3 && t.contains(n)) {
                // 位号，如 T-101、R-201
                score += 80;
            } else if (n.matches("[\\u4e00-\\u9fa5]{2,}") && t.contains(n)) {
                score += 40;
                if (number != null && t.contains(number + n)) {
                    // “1号原料储罐”：编号也对上
                    score += 30;
                }
            }
        }
        return score;
    }

    /** 统一写法：去掉空格和符号，小写，“一号”→“1” */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String lower = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            int d = CN_DIGITS.indexOf(c);
            if (d >= 0 && i + 1 < lower.length() && "号#＃".indexOf(lower.charAt(i + 1)) >= 0) {
                sb.append(d);
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
            // 号、#、-、空格等符号都去掉
        }
        return sb.toString().replace("号", "");
    }

    static String spaceLocation(SpaceRef s) {
        return truncate((s.location() == null || s.location().isBlank() ? "" : s.location() + " ") + s.name(), MAX_LOCATION);
    }

    // ============================== 校验大模型的结果 ==============================

    /**
     * 大模型返回的结果不能直接用：类别、等级必须是固定值，受限空间编号必须存在，长度要截断。
     * 模型没给出的字段用关键词规则补上。
     */
    public static HazardDraft sanitize(HazardDraft ai, String text, List<SpaceRef> spaces) {
        HazardDraft rule = extract(text, spaces);
        if (ai == null) {
            return rule;
        }
        String category = CATEGORIES.contains(trim(ai.category())) ? trim(ai.category()) : rule.category();
        String level = normalizeLevel(ai.level());
        String reason = trim(ai.levelReason());
        if (level == null) {
            level = rule.level();
            reason = trim(rule.levelReason());
        }
        SpaceRef space = spaces.stream().filter(s -> s.code().equalsIgnoreCase(trim(ai.spaceCode()))).findFirst()
                .orElseGet(() -> matchSpace(clean(text), spaces));
        String location = trim(ai.location());
        if (location.isEmpty()) {
            location = space != null ? spaceLocation(space) : rule.location();
        }
        String title = trim(ai.title());
        String description = trim(ai.description());
        return new HazardDraft(
                truncate(title.isEmpty() ? rule.title() : title, MAX_TITLE),
                category, level,
                truncate(location, MAX_LOCATION),
                space == null ? null : space.code(),
                truncate(description.isEmpty() ? rule.description() : description, MAX_DESCRIPTION),
                reason.isEmpty() ? null : truncate(reason, 100));
    }

    static String normalizeLevel(String level) {
        String l = trim(level).toUpperCase(Locale.ROOT);
        if (l.equals(MAJOR) || l.contains("重大")) {
            return MAJOR;
        }
        if (l.equals(GENERAL) || l.contains("一般")) {
            return GENERAL;
        }
        return null;
    }

    /** 给上报人的提示：哪些内容需要补充、核对 */
    public static List<String> notes(HazardDraft d) {
        List<String> notes = new ArrayList<>();
        if (d.location() == null || d.location().isBlank()) {
            notes.add("没有听出具体位置，请补充");
        }
        if (MAJOR.equals(d.level())) {
            notes.add("判定为重大隐患" + (d.levelReason() == null ? "" : "（" + d.levelReason() + "）") + "，提交后会立即通知安全员，请确认");
        }
        return notes;
    }

    static String clean(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
