package com.safework.system.job;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台端：定时任务 */
@RestController
@RequestMapping("/platform/job")
@RequiredArgsConstructor
public class JobController {

    private final JobRunner jobRunner;

    @SaCheckPermission("platform:job:list")
    @GetMapping("/list")
    public R<List<JobVO>> list() {
        return R.ok(jobRunner.list());
    }

    @SaCheckPermission("platform:job:list")
    @GetMapping("/log/page")
    public R<Page<SysJobLog>> logs(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "10") long size,
                                   @RequestParam(required = false) String code,
                                   @RequestParam(required = false) Integer status) {
        return R.ok(jobRunner.logs(current, size, code, status));
    }

    /** 立即执行一次（同步执行，返回本次执行记录） */
    @OperLog(module = "定时任务", action = "手动执行")
    @SaCheckPermission("platform:job:run")
    @PostMapping("/{code}/run")
    public R<SysJobLog> run(@PathVariable String code) {
        Object name = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
        return R.ok(jobRunner.runManually(code, name == null ? null : name.toString()));
    }
}
