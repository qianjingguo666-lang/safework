package com.safework.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.system.dto.DeptSaveDTO;
import com.safework.system.dto.DeptVO;
import com.safework.system.service.DeptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    /** 部门树：登录即可查看（用户管理、受限空间等页面的部门下拉都要用） */
    @GetMapping("/tree")
    public R<List<DeptVO>> tree() {
        return R.ok(deptService.tree());
    }

    @OperLog(module = "部门管理", action = "新增部门")
    @SaCheckPermission("system:dept:add")
    @PostMapping
    public R<Long> create(@Valid @RequestBody DeptSaveDTO dto) {
        return R.ok(deptService.create(dto));
    }

    @OperLog(module = "部门管理", action = "修改部门")
    @SaCheckPermission("system:dept:edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DeptSaveDTO dto) {
        deptService.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "部门管理", action = "删除部门")
    @SaCheckPermission("system:dept:remove")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        deptService.remove(id);
        return R.ok();
    }
}
