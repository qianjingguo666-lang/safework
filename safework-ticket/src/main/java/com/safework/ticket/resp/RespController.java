package com.safework.ticket.resp;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "安全生产责任制", description = "岗位安全职责、年度责任书发布与手写签署、年度考核")
@RestController
@RequestMapping("/safety/resp")
@RequiredArgsConstructor
public class RespController {

    private final RespService service;

    // ---------------- 我的责任书（所有登录用户） ----------------

    @Operation(summary = "我的责任书（每个年度一份）")
    @GetMapping("/mine")
    public R<List<RespVOs.Book>> mine() {
        return R.ok(service.mine());
    }

    @Operation(summary = "我在某份责任书里的记录 ID")
    @GetMapping("/mine/{bookId}")
    public R<Long> mySign(@PathVariable Long bookId) {
        return R.ok(service.mySignId(bookId));
    }

    @Operation(summary = "一份责任书的完整内容（本人或管理员）：通用条款、岗位职责、签名、考核")
    @GetMapping("/sign/{id}")
    public R<RespVOs.SignDetail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "安全生产责任制", action = "签署责任书")
    @Operation(summary = "本人手写签署")
    @PostMapping(value = "/sign/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<Void> sign(@PathVariable Long id, @Valid @RequestBody RespDTOs.Sign dto) {
        service.sign(id, dto.getSignature());
        return R.ok();
    }

    // ---------------- 岗位职责 ----------------

    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "岗位安全职责（岗位在【劳保用品 → 岗位标准】里维护）")
    @GetMapping("/duty/list")
    public R<List<RespVOs.PostDuty>> duties() {
        return R.ok(service.postDuties());
    }

    @OperLog(module = "安全生产责任制", action = "修改岗位职责")
    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "保存岗位安全职责（只影响以后发布的责任书）")
    @PutMapping("/duty/{postId}")
    public R<Void> saveDuties(@PathVariable Long postId, @RequestBody RespDTOs.Duties dto) {
        service.saveDuties(postId, dto.getDuties());
        return R.ok();
    }

    // ---------------- 年度责任书 ----------------

    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "年度责任书列表（含签署、考核进度）")
    @GetMapping("/book/list")
    public R<List<RespVOs.Book>> books() {
        return R.ok(service.books());
    }

    @OperLog(module = "安全生产责任制", action = "新建责任书")
    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "新建年度责任书（草稿）")
    @PostMapping("/book")
    public R<Long> create(@Valid @RequestBody RespDTOs.BookSave dto) {
        return R.ok(service.saveBook(null, dto));
    }

    @OperLog(module = "安全生产责任制", action = "修改责任书")
    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "修改责任书（只有草稿能改）")
    @PutMapping("/book/{id}")
    public R<Long> update(@PathVariable Long id, @Valid @RequestBody RespDTOs.BookSave dto) {
        return R.ok(service.saveBook(id, dto));
    }

    @OperLog(module = "安全生产责任制", action = "删除责任书")
    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "删除草稿")
    @DeleteMapping("/book/{id}")
    public R<Void> remove(@PathVariable Long id) {
        service.removeDraft(id);
        return R.ok();
    }

    @OperLog(module = "安全生产责任制", action = "发布责任书")
    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "发布：给岗位上的每个人生成一份待签署的责任书（职责快照），返回人数")
    @PostMapping("/book/{id}/publish")
    public R<Integer> publish(@PathVariable Long id) {
        return R.ok(service.publish(id));
    }

    @OperLog(module = "安全生产责任制", action = "同步人员")
    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "给发布后新到岗位的人补发责任书，返回补发人数")
    @PostMapping("/book/{id}/sync")
    public R<Integer> sync(@PathVariable Long id) {
        return R.ok(service.syncMembers(id));
    }

    @OperLog(module = "安全生产责任制", action = "结束签署")
    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "结束签署")
    @PostMapping("/book/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        service.close(id);
        return R.ok();
    }

    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "签署情况：status = PENDING / SIGNED；unassessed=true 只看没考核的")
    @GetMapping("/book/{id}/signs")
    public R<RespVOs.SignPage> signs(@PathVariable Long id, RespDTOs.SignQuery query) {
        return R.ok(service.signs(id, query));
    }

    // ---------------- 年度考核 ----------------

    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "考核参考数据：这一年里上报隐患、按期整改、按时检查、完成作业票")
    @GetMapping("/sign/{id}/reference")
    public R<RespVOs.Reference> reference(@PathVariable Long id) {
        return R.ok(service.reference(id));
    }

    @OperLog(module = "安全生产责任制", action = "年度考核")
    @SaCheckPermission(RespService.PERM_EDIT)
    @Operation(summary = "年度考核打分（0 ~ 100），返回等级")
    @PostMapping("/sign/{id}/assess")
    public R<Map<String, String>> assess(@PathVariable Long id, @Valid @RequestBody RespDTOs.Assess dto) {
        return R.ok(Map.of("grade", service.assess(id, dto.getScore(), dto.getComment())));
    }
}
