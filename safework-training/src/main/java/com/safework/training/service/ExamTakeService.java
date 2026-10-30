package com.safework.training.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.system.entity.SysUser;
import com.safework.system.service.DeptService;
import com.safework.system.service.UserService;
import com.safework.training.core.Grader;
import com.safework.training.core.PaperBuilder;
import com.safework.training.core.PaperItem;
import com.safework.training.dto.MyExamVO;
import com.safework.training.dto.PaperVO;
import com.safework.training.dto.ResultVO;
import com.safework.training.entity.TrainExam;
import com.safework.training.entity.TrainExamRecord;
import com.safework.training.enums.ExamStatus;
import com.safework.training.enums.RecordStatus;
import com.safework.training.mapper.TrainExamMapper;
import com.safework.training.mapper.TrainExamRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 考生答题：开始考试（随机组卷）→ 自动保存答案 → 交卷（自动阅卷）→ 查看成绩。
 * <p>
 * 时间以服务器为准：每张答卷有交卷截止时间 deadline，前端倒计时只是提示；
 * 超时后提交的答案不再接受（留 60 秒网络延迟余量），定时任务会把超时没交的卷按已保存的答案自动交卷。
 */
@Service
@RequiredArgsConstructor
public class ExamTakeService {

    /** 截止后仍接受提交的余量（网络延迟、倒计时误差） */
    static final int GRACE_SECONDS = 60;
    /** 查询答卷列表时不取大字段 */
    private static final List<String> LIGHT_COLUMNS = List.of("paper", "answers");

    private final TrainExamMapper examMapper;
    private final TrainExamRecordMapper recordMapper;
    private final QuestionService questionService;
    private final UserService userService;
    private final DeptService deptService;
    private final PaperCodec codec;
    private final SecureRandom random = new SecureRandom();

    /** 我的考试：已发布、已结束，并且参考范围包含我的部门 */
    public List<MyExamVO> myExams() {
        SysUser me = me();
        List<TrainExam> exams = examMapper.selectList(Wrappers.<TrainExam>lambdaQuery()
                .in(TrainExam::getStatus, ExamStatus.PUBLISHED, ExamStatus.CLOSED)
                // 参考范围：全体员工（target_dept_ids 为空），或者包含我所在的部门
                .isNull(me.getDeptId() == null, TrainExam::getTargetDeptIds)
                .and(me.getDeptId() != null, w -> w.isNull(TrainExam::getTargetDeptIds)
                        .or().apply("FIND_IN_SET({0}, target_dept_ids) > 0", me.getDeptId()))
                .orderByDesc(TrainExam::getStartTime)
                .last("LIMIT 100"));
        if (exams.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TrainExamRecord>> mine = recordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                        .select(TrainExamRecord.class, f -> !LIGHT_COLUMNS.contains(f.getColumn()))
                        .eq(TrainExamRecord::getUserId, me.getId())
                        .in(TrainExamRecord::getExamId, exams.stream().map(TrainExam::getId).toList()))
                .stream().collect(Collectors.groupingBy(TrainExamRecord::getExamId));
        LocalDateTime now = LocalDateTime.now();
        return exams.stream().map(e -> toMyExam(e, mine.getOrDefault(e.getId(), List.of()), now)).toList();
    }

    MyExamVO toMyExam(TrainExam e, List<TrainExamRecord> records, LocalDateTime now) {
        MyExamVO vo = new MyExamVO();
        vo.setExamId(e.getId());
        vo.setTitle(e.getTitle());
        vo.setDescription(e.getDescription());
        vo.setQuestionCount(e.getSingleCount() + e.getMultiCount() + e.getJudgeCount());
        vo.setTotalScore(e.getTotalScore());
        vo.setPassScore(e.getPassScore());
        vo.setDurationMinutes(e.getDurationMinutes());
        vo.setMaxAttempts(e.getMaxAttempts());
        vo.setStartTime(e.getStartTime());
        vo.setEndTime(e.getEndTime());
        vo.setAttempts(records.size());
        TrainExamRecord best = records.stream()
                .filter(r -> r.getStatus() == RecordStatus.SUBMITTED)
                .max(Comparator.comparing(TrainExamRecord::getScore))
                .orElse(null);
        TrainExamRecord doing = records.stream()
                .filter(r -> r.getStatus() == RecordStatus.IN_PROGRESS && !r.getDeadline().isBefore(now))
                .findFirst().orElse(null);
        boolean passed = best != null && Integer.valueOf(1).equals(best.getPassed());
        vo.setBestScore(best == null ? null : best.getScore());
        vo.setBestRecordId(best == null ? null : best.getId());
        vo.setPassed(best == null ? null : passed);
        vo.setInProgressRecordId(doing == null ? null : doing.getId());
        boolean open = e.getStatus() == ExamStatus.PUBLISHED && !now.isBefore(e.getStartTime()) && now.isBefore(e.getEndTime());
        boolean attemptsLeft = records.size() < e.getMaxAttempts();
        String state;
        if (doing != null) {
            state = "IN_PROGRESS";
        } else if (passed) {
            state = "PASSED";
        } else if (now.isBefore(e.getStartTime()) && e.getStatus() == ExamStatus.PUBLISHED) {
            state = "NOT_STARTED";
        } else if (best != null) {
            state = open && attemptsLeft ? "RETRY" : "FAILED";
        } else {
            state = open && attemptsLeft ? "TODO" : "MISSED";
        }
        vo.setState(state);
        vo.setCanStart(doing != null || (open && attemptsLeft && !passed));
        return vo;
    }

    /** 开始考试：有没交的卷就继续答，否则随机组一张新卷 */
    public PaperVO start(Long examId) {
        SysUser me = me();
        TrainExam exam = visibleExam(examId, me);
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStatus() != ExamStatus.PUBLISHED || now.isBefore(exam.getStartTime()) || !now.isBefore(exam.getEndTime())) {
            throw new BizException("不在考试时间内（" + exam.getStartTime().format(ExamService.FMT) + " ~ "
                    + exam.getEndTime().format(ExamService.FMT) + "）");
        }
        List<TrainExamRecord> records = recordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                .eq(TrainExamRecord::getExamId, examId)
                .eq(TrainExamRecord::getUserId, me.getId()));
        for (TrainExamRecord r : records) {
            if (r.getStatus() == RecordStatus.IN_PROGRESS) {
                if (!r.getDeadline().isBefore(now)) {
                    return codec.toPaperVO(r, exam.getDurationMinutes(), exam.getPassScore());
                }
                grade(r, codec.answers(r), true);
            }
        }
        if (records.stream().anyMatch(r -> Integer.valueOf(1).equals(r.getPassed()))) {
            throw new BizException("你已经通过了这场考试");
        }
        if (records.size() >= exam.getMaxAttempts()) {
            throw new BizException("考试次数已用完（最多 " + exam.getMaxAttempts() + " 次）");
        }

        List<PaperItem> paper;
        try {
            paper = PaperBuilder.build(questionService.enabledByType(exam.getCategory()), ExamService.rules(exam), random);
        } catch (IllegalStateException e) {
            throw new BizException("题库题目不足，无法组卷，请联系管理员：" + e.getMessage());
        }
        TrainExamRecord r = new TrainExamRecord();
        r.setExamId(examId);
        r.setExamTitle(exam.getTitle());
        r.setUserId(me.getId());
        r.setUserName(me.getNickname());
        r.setDeptId(me.getDeptId());
        r.setDeptName(deptService.nameOf(me.getDeptId()));
        r.setAttemptNo(records.size() + 1);
        r.setPaper(codec.writePaper(paper));
        r.setAnswers("{}");
        r.setScore(0);
        r.setTotalScore(Grader.totalScore(paper));
        r.setPassed(0);
        r.setStatus(RecordStatus.IN_PROGRESS);
        r.setStartTime(now);
        LocalDateTime deadline = now.plusMinutes(exam.getDurationMinutes());
        r.setDeadline(deadline.isAfter(exam.getEndTime()) ? exam.getEndTime() : deadline);
        r.setAutoSubmit(0);
        try {
            recordMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 连点两次“开始考试”：唯一索引（考试 + 人 + 第几次）挡住了第二次，返回已经生成的那张卷
            TrainExamRecord existing = recordMapper.selectOne(Wrappers.<TrainExamRecord>lambdaQuery()
                    .eq(TrainExamRecord::getExamId, examId)
                    .eq(TrainExamRecord::getUserId, me.getId())
                    .eq(TrainExamRecord::getAttemptNo, r.getAttemptNo()));
            return codec.toPaperVO(existing, exam.getDurationMinutes(), exam.getPassScore());
        }
        return codec.toPaperVO(r, exam.getDurationMinutes(), exam.getPassScore());
    }

    /** 继续答题（刷新页面后）。时间已到会自动交卷并提示 */
    public PaperVO paper(Long recordId) {
        TrainExamRecord r = myRecord(recordId);
        if (r.getStatus() != RecordStatus.IN_PROGRESS) {
            throw new BizException(409, "已经交卷了");
        }
        if (LocalDateTime.now().isAfter(r.getDeadline().plusSeconds(GRACE_SECONDS))) {
            grade(r, codec.answers(r), true);
            throw new BizException(409, "考试时间已到，系统已按保存的答案自动交卷");
        }
        TrainExam exam = examMapper.selectById(r.getExamId());
        return codec.toPaperVO(r, exam == null ? null : exam.getDurationMinutes(), exam == null ? null : exam.getPassScore());
    }

    /** 自动保存答案（前端每改一题就保存，断网、关浏览器不丢答案） */
    public void saveAnswers(Long recordId, Map<Integer, String> answers) {
        TrainExamRecord r = myRecord(recordId);
        if (r.getStatus() != RecordStatus.IN_PROGRESS) {
            throw new BizException(409, "已经交卷了");
        }
        if (LocalDateTime.now().isAfter(r.getDeadline().plusSeconds(GRACE_SECONDS))) {
            throw new BizException(409, "考试时间已到，不能再修改答案");
        }
        TrainExamRecord change = new TrainExamRecord();
        change.setAnswers(codec.writeAnswers(codec.clean(codec.paper(r), answers)));
        recordMapper.update(change, Wrappers.<TrainExamRecord>lambdaUpdate()
                .eq(TrainExamRecord::getId, recordId)
                .eq(TrainExamRecord::getStatus, RecordStatus.IN_PROGRESS));
    }

    /** 交卷：超时太久只按已保存的答案阅卷 */
    public ResultVO submit(Long recordId, Map<Integer, String> answers) {
        TrainExamRecord r = myRecord(recordId);
        if (r.getStatus() == RecordStatus.IN_PROGRESS) {
            boolean inTime = !LocalDateTime.now().isAfter(r.getDeadline().plusSeconds(GRACE_SECONDS));
            Map<Integer, String> effective = codec.answers(r);
            if (inTime && answers != null) {
                effective.putAll(answers);
            }
            grade(r, effective, !inTime);
            r = recordMapper.selectById(recordId);
        }
        return result(r);
    }

    public ResultVO result(Long recordId) {
        TrainExamRecord r = myRecord(recordId);
        if (r.getStatus() != RecordStatus.SUBMITTED) {
            throw new BizException(409, "还没有交卷");
        }
        return result(r);
    }

    private ResultVO result(TrainExamRecord r) {
        TrainExam exam = examMapper.selectById(r.getExamId());
        boolean show = exam != null && Integer.valueOf(1).equals(exam.getShowAnswer());
        return codec.toResultVO(r, exam == null ? null : exam.getPassScore(), show);
    }

    /**
     * 阅卷并把状态改成已交卷。用“WHERE status = 答题中”做条件更新：
     * 考生点交卷和定时任务自动交卷同时发生时，只有一个会成功，分数不会被覆盖。
     *
     * @return 是否由这次调用完成交卷
     */
    boolean grade(TrainExamRecord r, Map<Integer, String> answers, boolean auto) {
        List<PaperItem> paper = codec.paper(r);
        Map<Integer, String> clean = codec.clean(paper, answers);
        int score = Grader.score(paper, clean);
        TrainExam exam = examMapper.selectById(r.getExamId());
        int pass = exam == null ? Integer.MAX_VALUE : exam.getPassScore();
        TrainExamRecord change = new TrainExamRecord();
        change.setAnswers(codec.writeAnswers(clean));
        change.setScore(score);
        change.setPassed(score >= pass ? 1 : 0);
        change.setStatus(RecordStatus.SUBMITTED);
        change.setSubmitTime(LocalDateTime.now());
        change.setAutoSubmit(auto ? 1 : 0);
        return recordMapper.update(change, Wrappers.<TrainExamRecord>lambdaUpdate()
                .eq(TrainExamRecord::getId, r.getId())
                .eq(TrainExamRecord::getStatus, RecordStatus.IN_PROGRESS)) > 0;
    }

    /** 定时任务 / 关闭考试时调用：把超时没交的卷自动交卷（当前租户） */
    public int autoSubmitExpired(Long examId, boolean all) {
        LocalDateTime limit = LocalDateTime.now().minusSeconds(GRACE_SECONDS);
        List<TrainExamRecord> list = recordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                .eq(TrainExamRecord::getStatus, RecordStatus.IN_PROGRESS)
                .eq(examId != null, TrainExamRecord::getExamId, examId)
                .lt(!all, TrainExamRecord::getDeadline, limit));
        int count = 0;
        for (TrainExamRecord r : list) {
            if (grade(r, codec.answers(r), true)) {
                count++;
            }
        }
        return count;
    }

    private TrainExam visibleExam(Long examId, SysUser me) {
        TrainExam exam = examMapper.selectById(examId);
        if (exam == null || exam.getStatus() == ExamStatus.DRAFT || !ExamService.targets(exam, me.getDeptId())) {
            throw new BizException(404, "考试不存在");
        }
        return exam;
    }

    /** 只能操作自己的答卷：别人的答卷一律返回“不存在”，不暴露是否存在 */
    private TrainExamRecord myRecord(Long recordId) {
        TrainExamRecord r = recordMapper.selectById(recordId);
        if (r == null || !Objects.equals(r.getUserId(), StpUtil.getLoginIdAsLong())) {
            throw new BizException(404, "答卷不存在");
        }
        return r;
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    /** 按 ID 列表批量查询答卷时用（不含大字段） */
    List<TrainExamRecord> lightRecords(Long examId) {
        return new ArrayList<>(recordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                .select(TrainExamRecord.class, f -> !LIGHT_COLUMNS.contains(f.getColumn()))
                .eq(TrainExamRecord::getExamId, examId)));
    }
}
