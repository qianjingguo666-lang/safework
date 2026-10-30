package com.safework.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.training.dto.IdsDTO;
import com.safework.training.dto.QuestionQueryDTO;
import com.safework.training.dto.QuestionSaveDTO;
import com.safework.training.dto.QuestionVO;
import com.safework.training.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "培训题库", description = "题目增删改、审核 AI 生成的题")
@RestController
@RequestMapping("/training/question")
@RequiredArgsConstructor
public class QuestionController {

    static final String PERM_EDIT = "training:question:edit";

    private final QuestionService questionService;

    @SaCheckPermission("training:question:list")
    @Operation(summary = "题目分页")
    @GetMapping("/page")
    public R<IPage<QuestionVO>> page(QuestionQueryDTO query) {
        return R.ok(questionService.page(query));
    }

    @SaCheckPermission(value = {"training:question:list", "training:exam:list"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @Operation(summary = "已有分类")
    @GetMapping("/categories")
    public R<List<String>> categories() {
        return R.ok(questionService.categories());
    }

    @OperLog(module = "培训题库", action = "新增题目")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "新增题目")
    @PostMapping
    public R<Long> create(@Valid @RequestBody QuestionSaveDTO dto) {
        return R.ok(questionService.create(dto));
    }

    @OperLog(module = "培训题库", action = "修改题目")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "修改题目")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody QuestionSaveDTO dto) {
        questionService.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "培训题库", action = "审核通过")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "审核通过（批量），返回实际处理的数量")
    @PostMapping("/approve")
    public R<Integer> approve(@Valid @RequestBody IdsDTO dto) {
        return R.ok(questionService.approve(dto.getIds()));
    }

    @OperLog(module = "培训题库", action = "启用停用")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "启用（1）/ 停用（2）")
    @PutMapping("/{id}/status")
    public R<Void> status(@PathVariable Long id, @RequestParam Integer status) {
        questionService.changeStatus(id, status);
        return R.ok();
    }

    @OperLog(module = "培训题库", action = "删除题目")
    @SaCheckPermission(PERM_EDIT)
    @Operation(summary = "删除（批量）")
    @PostMapping("/remove")
    public R<Integer> remove(@Valid @RequestBody IdsDTO dto) {
        return R.ok(questionService.remove(dto.getIds()));
    }
}
