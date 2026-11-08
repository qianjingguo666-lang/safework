package com.safework.ticket.commit;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@Tag(name = "安全风险研判与承诺", description = "生产、设备、工艺、安全负责人每天研判，主要负责人手写签名承诺并公告（危险化学品企业安全风险研判与承诺公告制度）")
@RestController
@RequestMapping("/safety/commit")
@RequiredArgsConstructor
public class CommitController {

    private final CommitService service;

    @SaCheckPermission(CommitService.PERM_LIST)
    @Operation(summary = "字典：研判方面和项目、截止时间")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.of("aspects", CommitRules.ASPECTS, "items", CommitRules.ITEMS,
                "judgeDeadline", CommitRules.JUDGE_DEADLINE.toString(), "commitDeadline", CommitRules.COMMIT_DEADLINE.toString()));
    }

    @SaCheckPermission(CommitService.PERM_LIST)
    @Operation(summary = "今天：各方面研判情况、系统统计、还差哪些、是否超时、我能不能研判 / 承诺")
    @GetMapping("/today")
    public R<CommitVOs.Today> today() {
        return R.ok(service.today());
    }

    @SaCheckPermission(CommitService.PERM_LIST)
    @Operation(summary = "承诺公告（不传日期是最近一次的）")
    @GetMapping("/board")
    public R<CommitVOs.Board> board(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(service.board(date));
    }

    @SaCheckPermission(CommitService.PERM_LIST)
    @Operation(summary = "某月记录和按时承诺率（月份 yyyy-MM，不传是本月）")
    @GetMapping("/history")
    public R<CommitVOs.History> history(@RequestParam(required = false) String month) {
        YearMonth m;
        try {
            m = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        } catch (Exception e) {
            throw new com.safework.common.exception.BizException("月份格式是 yyyy-MM");
        }
        return R.ok(service.history(m));
    }

    @OperLog(module = "风险研判承诺", action = "研判")
    @SaCheckPermission(CommitService.PERM_JUDGE)
    @Operation(summary = "研判（一个方面一次，承诺前可以重新研判）")
    @PostMapping("/judge")
    public R<Void> judge(@Valid @RequestBody CommitDTOs.Judge dto) {
        service.judge(dto);
        return R.ok();
    }

    @OperLog(module = "风险研判承诺", action = "承诺公告")
    @SaCheckPermission(CommitService.PERM_SIGN)
    @Operation(summary = "主要负责人手写签名承诺（四个方面都研判后）")
    @PostMapping("/sign")
    public R<Void> commit(@Valid @RequestBody CommitDTOs.Commit dto) {
        service.commit(dto);
        return R.ok();
    }
}
