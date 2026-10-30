package com.safework.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.training.dto.AnswersDTO;
import com.safework.training.dto.MyExamVO;
import com.safework.training.dto.PaperVO;
import com.safework.training.dto.ResultVO;
import com.safework.training.service.ExamService;
import com.safework.training.service.ExamTakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "我的考试", description = "开始考试、自动保存答案、交卷、查看成绩")
@RestController
@RequestMapping("/training/my")
@RequiredArgsConstructor
public class MyExamController {

    private final ExamTakeService takeService;

    @SaCheckPermission(ExamService.PERM_TAKE)
    @Operation(summary = "我的考试列表")
    @GetMapping("/exams")
    public R<List<MyExamVO>> exams() {
        return R.ok(takeService.myExams());
    }

    @SaCheckPermission(ExamService.PERM_TAKE)
    @Operation(summary = "开始考试（有没交的卷就继续）")
    @PostMapping("/exams/{examId}/start")
    public R<PaperVO> start(@PathVariable Long examId) {
        return R.ok(takeService.start(examId));
    }

    @SaCheckPermission(ExamService.PERM_TAKE)
    @Operation(summary = "继续答题")
    @GetMapping("/record/{id}/paper")
    public R<PaperVO> paper(@PathVariable Long id) {
        return R.ok(takeService.paper(id));
    }

    @SaCheckPermission(ExamService.PERM_TAKE)
    @Operation(summary = "保存答案")
    @PutMapping("/record/{id}/answers")
    public R<Void> save(@PathVariable Long id, @RequestBody AnswersDTO dto) {
        takeService.saveAnswers(id, dto.getAnswers());
        return R.ok();
    }

    @OperLog(module = "我的考试", action = "交卷")
    @SaCheckPermission(ExamService.PERM_TAKE)
    @Operation(summary = "交卷，返回成绩")
    @PostMapping("/record/{id}/submit")
    public R<ResultVO> submit(@PathVariable Long id, @RequestBody(required = false) AnswersDTO dto) {
        return R.ok(takeService.submit(id, dto == null ? null : dto.getAnswers()));
    }

    @SaCheckPermission(ExamService.PERM_TAKE)
    @Operation(summary = "查看成绩")
    @GetMapping("/record/{id}/result")
    public R<ResultVO> result(@PathVariable Long id) {
        return R.ok(takeService.result(id));
    }
}
