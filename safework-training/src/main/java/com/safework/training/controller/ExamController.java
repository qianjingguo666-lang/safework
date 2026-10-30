package com.safework.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.training.dto.ExamQueryDTO;
import com.safework.training.dto.ExamSaveDTO;
import com.safework.training.dto.ExamStatsVO;
import com.safework.training.dto.ResultVO;
import com.safework.training.entity.TrainExam;
import com.safework.training.entity.TrainExamRecord;
import com.safework.training.enums.QuestionType;
import com.safework.training.service.ExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "考试管理", description = "建考试（随机组卷规则）、发布、结束、成绩统计和导出")
@RestController
@RequestMapping("/training/exam")
@RequiredArgsConstructor
public class ExamController {

    static final String PERM_LIST = "training:exam:list";
    static final String PERM_EDIT = "training:exam:edit";

    private final ExamService examService;

    @SaCheckPermission(PERM_LIST)
    @Operation(summary = "考试分页（带已交卷、通过人数）")
    @GetMapping("/page")
    public R<Page<TrainExam>> page(ExamQueryDTO query) {
        return R.ok(examService.page(query));
    }

    @SaCheckPermission(PERM_LIST)
    @Operation(summary = "考试详情")
    @GetMapping("/{id}")
    public R<TrainExam> get(@PathVariable Long id) {
        return R.ok(examService.get(id));
    }

    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "题库里每种题型可用的题目数量")
    @GetMapping("/question-count")
    public R<Map<QuestionType, Long>> questionCount(@RequestParam(required = false) String category) {
        return R.ok(examService.questionCount(category));
    }

    @OperLog(module = "考试管理", action = "新建考试")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "新建考试（草稿）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ExamSaveDTO dto) {
        return R.ok(examService.create(dto));
    }

    @OperLog(module = "考试管理", action = "修改考试")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "修改考试（只能改草稿）")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ExamSaveDTO dto) {
        examService.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "考试管理", action = "发布考试")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "发布并通知参考人员，返回通知人数")
    @PostMapping("/{id}/publish")
    public R<Integer> publish(@PathVariable Long id) {
        return R.ok(examService.publish(id));
    }

    @OperLog(module = "考试管理", action = "结束考试")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "提前结束，正在答题的人自动交卷，返回自动交卷数量")
    @PostMapping("/{id}/close")
    public R<Integer> close(@PathVariable Long id) {
        return R.ok(examService.close(id));
    }

    @OperLog(module = "考试管理", action = "删除考试")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "删除草稿")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        examService.remove(id);
        return R.ok();
    }

    @SaCheckPermission(PERM_LIST)
    @Operation(summary = "成绩统计：通过率、分数段、错误率最高的题、未参加名单")
    @GetMapping("/{id}/stats")
    public R<ExamStatsVO> stats(@PathVariable Long id) {
        return R.ok(examService.stats(id));
    }

    @SaCheckPermission(PERM_LIST)
    @Operation(summary = "答卷列表")
    @GetMapping("/{id}/records")
    public R<Page<TrainExamRecord>> records(@PathVariable Long id, @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword) {
        return R.ok(examService.records(id, current, size, keyword));
    }

    @SaCheckPermission(PERM_LIST)
    @Operation(summary = "查看某份答卷")
    @GetMapping("/record/{recordId}")
    public R<ResultVO> record(@PathVariable Long recordId) {
        return R.ok(examService.record(recordId));
    }

    @SaCheckPermission(PERM_LIST)
    @Operation(summary = "导出成绩（Excel）")
    @GetMapping("/{id}/export")
    public void export(@PathVariable Long id, HttpServletResponse response) {
        examService.export(id, response);
    }
}
