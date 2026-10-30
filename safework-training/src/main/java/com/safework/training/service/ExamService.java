package com.safework.training.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.system.entity.SysUser;
import com.safework.system.excel.ExcelHelper;
import com.safework.system.mapper.SysUserMapper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.DeptService;
import com.safework.system.service.UserService;
import com.safework.training.core.Grader;
import com.safework.training.core.PaperBuilder;
import com.safework.training.core.PaperItem;
import com.safework.training.dto.ExamQueryDTO;
import com.safework.training.dto.ExamSaveDTO;
import com.safework.training.dto.ExamStatsVO;
import com.safework.training.dto.ResultVO;
import com.safework.training.entity.TrainExam;
import com.safework.training.entity.TrainExamRecord;
import com.safework.training.enums.ExamStatus;
import com.safework.training.enums.QuestionType;
import com.safework.training.enums.RecordStatus;
import com.safework.training.excel.ExamResultRow;
import com.safework.training.mapper.TrainExamMapper;
import com.safework.training.mapper.TrainExamRecordMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 考试管理（安全员、管理员）：建考试、发布、结束、成绩统计 */
@Service
@RequiredArgsConstructor
public class ExamService {

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final String PERM_TAKE = "training:my";
    public static final String BIZ_TYPE = "EXAM";
    public static final String MY_LINK = "/training/my";

    private final TrainExamMapper examMapper;
    private final TrainExamRecordMapper recordMapper;
    private final QuestionService questionService;
    private final ExamTakeService takeService;
    private final UserService userService;
    private final DeptService deptService;
    private final SysUserMapper userMapper;
    private final MessageService messageService;
    private final PaperCodec codec;

    // ============================== 规则 ==============================

    /** 组卷规则 */
    static List<PaperBuilder.Rule> rules(TrainExam e) {
        return List.of(new PaperBuilder.Rule(QuestionType.SINGLE, e.getSingleCount(), e.getSingleScore()),
                new PaperBuilder.Rule(QuestionType.MULTI, e.getMultiCount(), e.getMultiScore()),
                new PaperBuilder.Rule(QuestionType.JUDGE, e.getJudgeCount(), e.getJudgeScore()));
    }

    /** 这场考试是否包含这个部门的人 */
    static boolean targets(TrainExam e, Long deptId) {
        if (!StringUtils.hasText(e.getTargetDeptIds())) {
            return true;
        }
        return deptId != null && Arrays.asList(e.getTargetDeptIds().split(",")).contains(String.valueOf(deptId));
    }

    // ============================== 查询 ==============================

    public Page<TrainExam> page(ExamQueryDTO q) {
        Page<TrainExam> page = examMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), Wrappers.<TrainExam>lambdaQuery()
                .eq(q.getStatus() != null, TrainExam::getStatus, q.getStatus())
                .like(StringUtils.hasText(q.getKeyword()), TrainExam::getTitle, q.getKeyword())
                .orderByDesc(TrainExam::getCreateTime));
        if (!page.getRecords().isEmpty()) {
            Map<Long, long[]> stats = new HashMap<>();
            for (Map<String, Object> row : recordMapper.selectMaps(new QueryWrapper<TrainExamRecord>()
                    .select("exam_id AS e", "COUNT(DISTINCT user_id) AS s", "COUNT(DISTINCT CASE WHEN passed = 1 THEN user_id END) AS p")
                    .eq("status", RecordStatus.SUBMITTED.name())
                    .in("exam_id", page.getRecords().stream().map(TrainExam::getId).toList())
                    .groupBy("exam_id"))) {
                stats.put(Long.valueOf(String.valueOf(row.get("e"))),
                        new long[]{((Number) row.get("s")).longValue(), ((Number) row.get("p")).longValue()});
            }
            page.getRecords().forEach(e -> {
                long[] s = stats.getOrDefault(e.getId(), new long[]{0, 0});
                e.setSubmittedUsers(s[0]);
                e.setPassedUsers(s[1]);
            });
        }
        return page;
    }

    public TrainExam get(Long id) {
        TrainExam e = examMapper.selectById(id);
        if (e == null) {
            throw new BizException(404, "考试不存在");
        }
        return e;
    }

    /** 题库里每种题型能用的题目数量 */
    public Map<QuestionType, Long> questionCount(String category) {
        return questionService.countEnabled(category);
    }

    // ============================== 编辑 ==============================

    public Long create(ExamSaveDTO dto) {
        TrainExam e = new TrainExam();
        fill(e, dto);
        e.setStatus(ExamStatus.DRAFT);
        SysUser me = userService.getById(StpUtil.getLoginIdAsLong());
        e.setCreatorName(me.getNickname());
        examMapper.insert(e);
        return e.getId();
    }

    public void update(Long id, ExamSaveDTO dto) {
        TrainExam e = get(id);
        if (e.getStatus() != ExamStatus.DRAFT) {
            throw new BizException("考试已发布，不能修改");
        }
        fill(e, dto);
        examMapper.updateById(e);
    }

    public void remove(Long id) {
        TrainExam e = get(id);
        if (e.getStatus() != ExamStatus.DRAFT) {
            throw new BizException("只能删除草稿；已发布的考试可以结束，成绩会保留");
        }
        examMapper.deleteById(id);
    }

    private void fill(TrainExam e, ExamSaveDTO dto) {
        int total = dto.getSingleCount() * dto.getSingleScore() + dto.getMultiCount() * dto.getMultiScore()
                + dto.getJudgeCount() * dto.getJudgeScore();
        if (dto.getSingleCount() + dto.getMultiCount() + dto.getJudgeCount() == 0) {
            throw new BizException("至少要有 1 道题");
        }
        if (dto.getPassScore() > total) {
            throw new BizException("及格分 " + dto.getPassScore() + " 不能高于总分 " + total);
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        e.setTitle(dto.getTitle().trim());
        e.setDescription(StringUtils.hasText(dto.getDescription()) ? dto.getDescription().trim() : null);
        e.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory().trim() : null);
        e.setSingleCount(dto.getSingleCount());
        e.setSingleScore(dto.getSingleScore());
        e.setMultiCount(dto.getMultiCount());
        e.setMultiScore(dto.getMultiScore());
        e.setJudgeCount(dto.getJudgeCount());
        e.setJudgeScore(dto.getJudgeScore());
        e.setTotalScore(total);
        e.setPassScore(dto.getPassScore());
        e.setDurationMinutes(dto.getDurationMinutes());
        e.setMaxAttempts(dto.getMaxAttempts());
        e.setStartTime(dto.getStartTime());
        e.setEndTime(dto.getEndTime());
        e.setShowAnswer(Boolean.FALSE.equals(dto.getShowAnswer()) ? 0 : 1);
        List<Long> deptIds = dto.getDeptIds() == null ? List.of() : dto.getDeptIds().stream().filter(Objects::nonNull).distinct().toList();
        if (deptIds.isEmpty()) {
            e.setDeptIds(null);
            e.setDeptNames(null);
            e.setTargetDeptIds(null);
        } else {
            // 选了上级部门，下级部门的人也要参加：保存时展开成全部部门 ID
            Set<Long> all = new LinkedHashSet<>();
            List<String> names = new ArrayList<>();
            for (Long id : deptIds) {
                names.add(deptService.getById(id).getDeptName());
                all.addAll(deptService.selfAndDescendants(id));
            }
            e.setDeptIds(deptIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
            e.setDeptNames(String.join("、", names));
            e.setTargetDeptIds(all.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
    }

    // ============================== 发布、结束 ==============================

    /** 发布：检查题库够不够组卷，然后通知参考人员 */
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long id) {
        TrainExam e = get(id);
        if (e.getStatus() != ExamStatus.DRAFT) {
            throw new BizException("考试已经发布过了");
        }
        if (!e.getEndTime().isAfter(LocalDateTime.now())) {
            throw new BizException("结束时间已过，请先修改考试时间");
        }
        Map<QuestionType, Long> counts = questionService.countEnabled(e.getCategory());
        List<String> lacks = new ArrayList<>();
        for (PaperBuilder.Rule r : rules(e)) {
            long have = counts.getOrDefault(r.type(), 0L);
            if (r.count() > have) {
                lacks.add(r.type().getLabel() + "需要 " + r.count() + " 道，只有 " + have + " 道");
            }
        }
        if (!lacks.isEmpty()) {
            throw new BizException("题库里" + (e.getCategory() == null ? "" : "【" + e.getCategory() + "】分类") + "启用的题目不够："
                    + String.join("；", lacks) + "。AI 生成的题需要审核通过后才能使用");
        }
        e.setStatus(ExamStatus.PUBLISHED);
        e.setPublishTime(LocalDateTime.now());
        examMapper.updateById(e);

        List<Long> users = targetUserIds(e);
        users.remove(StpUtil.getLoginIdAsLong());
        messageService.send(users, MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, e.getId(), "新考试：" + e.getTitle(),
                "考试时间 " + e.getStartTime().format(FMT) + " ~ " + e.getEndTime().format(FMT) + "，时长 " + e.getDurationMinutes()
                        + " 分钟，满分 " + e.getTotalScore() + " 分，" + e.getPassScore() + " 分及格", MY_LINK));
        return users.size();
    }

    /** 提前结束：正在答题的人按已保存的答案自动交卷 */
    public int close(Long id) {
        TrainExam e = get(id);
        if (e.getStatus() != ExamStatus.PUBLISHED) {
            throw new BizException("只有进行中的考试可以结束");
        }
        e.setStatus(ExamStatus.CLOSED);
        examMapper.updateById(e);
        return takeService.autoSubmitExpired(id, true);
    }

    /** 应参加考试的人：有“我的考试”权限，并且在参考部门里 */
    public List<Long> targetUserIds(TrainExam e) {
        List<Long> ids = new ArrayList<>(messageService.listPermissionUsers(PERM_TAKE));
        if (ids.isEmpty() || !StringUtils.hasText(e.getTargetDeptIds())) {
            return ids;
        }
        Set<String> depts = Set.of(e.getTargetDeptIds().split(","));
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId, SysUser::getDeptId)
                        .in(SysUser::getId, ids)).stream()
                .filter(u -> u.getDeptId() != null && depts.contains(String.valueOf(u.getDeptId())))
                .map(SysUser::getId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // ============================== 成绩 ==============================

    /** 统计：每人取最高分 */
    public ExamStatsVO stats(Long id) {
        TrainExam e = get(id);
        List<TrainExamRecord> submitted = recordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                .eq(TrainExamRecord::getExamId, id)
                .eq(TrainExamRecord::getStatus, RecordStatus.SUBMITTED));
        Map<Long, TrainExamRecord> best = bestPerUser(submitted);

        int passed = (int) best.values().stream().filter(r -> Integer.valueOf(1).equals(r.getPassed())).count();
        Double avg = best.isEmpty() ? null
                : Math.round(best.values().stream().mapToInt(TrainExamRecord::getScore).average().orElse(0) * 10) / 10.0;
        Integer max = best.values().stream().map(TrainExamRecord::getScore).max(Integer::compare).orElse(null);
        Integer min = best.values().stream().map(TrainExamRecord::getScore).min(Integer::compare).orElse(null);

        // 分数段按得分率划分，满分不是 100 的考试也能比较
        String[] names = {"60% 以下", "60~69%", "70~79%", "80~89%", "90% 以上"};
        int[] counts = new int[5];
        for (TrainExamRecord r : best.values()) {
            double rate = e.getTotalScore() == 0 ? 0 : r.getScore() * 100.0 / e.getTotalScore();
            counts[rate < 60 ? 0 : rate >= 90 ? 4 : (int) (rate / 10) - 5]++;
        }
        List<ExamStatsVO.Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            buckets.add(new ExamStatsVO.Bucket(names[i], counts[i]));
        }

        // 每道题的正确率（所有答卷都算，题目按题库 ID 合并）
        Map<Long, int[]> perQuestion = new LinkedHashMap<>();
        Map<Long, PaperItem> questionInfo = new HashMap<>();
        for (TrainExamRecord r : submitted) {
            Map<Integer, String> answers = codec.answers(r);
            for (PaperItem item : codec.paper(r)) {
                int[] c = perQuestion.computeIfAbsent(item.questionId(), k -> new int[2]);
                c[0]++;
                if (Grader.isCorrect(item.answer(), answers.get(item.index()))) {
                    c[1]++;
                }
                questionInfo.putIfAbsent(item.questionId(), item);
            }
        }
        List<ExamStatsVO.QuestionStat> hardest = perQuestion.entrySet().stream()
                .map(en -> {
                    PaperItem info = questionInfo.get(en.getKey());
                    int[] c = en.getValue();
                    return new ExamStatsVO.QuestionStat(en.getKey(), info.type(), info.content(), c[0], c[1],
                            Math.round(c[1] * 1000.0 / c[0]) / 10.0);
                })
                .sorted(Comparator.comparingDouble(ExamStatsVO.QuestionStat::rate))
                .limit(5)
                .toList();

        List<Long> targets = targetUserIds(e);
        List<Long> missingIds = targets.stream().filter(uid -> !best.containsKey(uid)).limit(100).toList();
        List<String> notTaken = missingIds.isEmpty() ? List.of() : userMapper.selectBatchIds(missingIds).stream()
                .map(SysUser::getNickname).toList();
        Double passRate = best.isEmpty() ? null : Math.round(passed * 1000.0 / best.size()) / 10.0;
        return new ExamStatsVO(e, targets.size(), best.size(), passed, passRate, avg, max, min, buckets, hardest, notTaken);
    }

    /** 答卷列表（每次交卷一条） */
    public Page<TrainExamRecord> records(Long examId, long current, long size, String keyword) {
        get(examId);
        return recordMapper.selectPage(new Page<>(current, size), Wrappers.<TrainExamRecord>lambdaQuery()
                .select(TrainExamRecord.class, f -> !"paper".equals(f.getColumn()) && !"answers".equals(f.getColumn()))
                .eq(TrainExamRecord::getExamId, examId)
                .eq(TrainExamRecord::getStatus, RecordStatus.SUBMITTED)
                .like(StringUtils.hasText(keyword), TrainExamRecord::getUserName, keyword)
                .orderByDesc(TrainExamRecord::getScore)
                .orderByAsc(TrainExamRecord::getSubmitTime));
    }

    /** 管理员查看某份答卷（总是显示正确答案） */
    public ResultVO record(Long recordId) {
        TrainExamRecord r = recordMapper.selectById(recordId);
        if (r == null || r.getStatus() != RecordStatus.SUBMITTED) {
            throw new BizException(404, "答卷不存在");
        }
        TrainExam e = examMapper.selectById(r.getExamId());
        return codec.toResultVO(r, e == null ? null : e.getPassScore(), true);
    }

    /** 导出成绩：每人一行（最高分），没参加的人也列出来 */
    public void export(Long id, HttpServletResponse response) {
        TrainExam e = get(id);
        List<TrainExamRecord> submitted = recordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                .select(TrainExamRecord.class, f -> !"paper".equals(f.getColumn()) && !"answers".equals(f.getColumn()))
                .eq(TrainExamRecord::getExamId, id)
                .eq(TrainExamRecord::getStatus, RecordStatus.SUBMITTED));
        Map<Long, Long> attempts = submitted.stream().collect(Collectors.groupingBy(TrainExamRecord::getUserId, Collectors.counting()));
        Map<Long, LocalDateTime> last = new HashMap<>();
        submitted.forEach(r -> last.merge(r.getUserId(), r.getSubmitTime(), (a, b) -> a.isAfter(b) ? a : b));
        List<ExamResultRow> rows = new ArrayList<>();
        bestPerUser(submitted).values().stream()
                .sorted(Comparator.comparing(TrainExamRecord::getScore).reversed())
                .forEach(r -> {
                    ExamResultRow row = new ExamResultRow();
                    row.setUserName(r.getUserName());
                    row.setDeptName(r.getDeptName());
                    row.setScore(r.getScore());
                    row.setPassed(Integer.valueOf(1).equals(r.getPassed()) ? "通过" : "未通过");
                    row.setAttempts(attempts.get(r.getUserId()).intValue());
                    row.setSubmitTime(last.get(r.getUserId()).format(FMT));
                    rows.add(row);
                });
        List<Long> missing = targetUserIds(e).stream().filter(uid -> !attempts.containsKey(uid)).toList();
        if (!missing.isEmpty()) {
            Map<Long, String> deptNames = deptService.nameMap();
            for (SysUser u : userMapper.selectBatchIds(missing)) {
                ExamResultRow row = new ExamResultRow();
                row.setUserName(u.getNickname());
                row.setDeptName(deptNames.get(u.getDeptId()));
                row.setPassed("未参加");
                row.setAttempts(0);
                rows.add(row);
            }
        }
        ExcelHelper.write(response, e.getTitle() + "_成绩", ExamResultRow.class, rows);
    }

    /** 每人最高分的那份答卷（同分取先交的） */
    static Map<Long, TrainExamRecord> bestPerUser(List<TrainExamRecord> records) {
        Map<Long, TrainExamRecord> best = new LinkedHashMap<>();
        for (TrainExamRecord r : records) {
            best.merge(r.getUserId(), r, (a, b) -> b.getScore() > a.getScore() ? b : a);
        }
        return best;
    }
}
