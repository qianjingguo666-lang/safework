package com.safework.ticket.std;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.HazardReportDTO;
import com.safework.safety.entity.Hazard;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.service.HazardService;
import com.safework.system.entity.SysUser;
import com.safework.system.excel.ExcelHelper;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.StdAssessment;
import com.safework.ticket.entity.StdItem;
import com.safework.ticket.mapper.StdAssessmentMapper;
import com.safework.ticket.mapper.StdItemMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安全生产标准化自评：
 * <pre>
 * 新建自评 ──复制 27 项考评项目快照──▶ 系统从各模块取证，自动项目给出“建议扣分”（先填进扣分，评审人确认或调整）
 *      │                                  手工项目（制度、文化等）评审人自己打分
 *      ├─ 逐项：空项 / 扣分（0.5 分为单位）/ 扣分说明；数据变了可以“重新取数”
 *      ▼
 * 完成自评：扣了分的必须写说明 → 得分（空项不计）+ 近一年安全绩效 → 等级（一级 / 二级 / 三级 / 未达标）
 *      │
 * 扣分项“转隐患整改”（走隐患闭环），下次自评检查是否闭环（8.2 持续改进）；和上一次自评按要素对比
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class StdService {

    public static final String PERM_LIST = "std:list";
    public static final String PERM_EDIT = "std:edit";
    public static final String LINK = "/safety/std";

    private final StdAssessmentMapper assessmentMapper;
    private final StdItemMapper itemMapper;
    private final StdEvidenceService evidenceService;
    private final HazardService hazardService;
    private final HazardMapper hazardMapper;
    private final UserService userService;
    private final ObjectMapper json = new ObjectMapper();

    public List<StdVOs.ListRow> list() {
        List<StdAssessment> list = assessmentMapper.selectList(Wrappers.<StdAssessment>lambdaQuery()
                .orderByDesc(StdAssessment::getStartDate).orderByDesc(StdAssessment::getCreateTime));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<StdItem>> items = itemMapper.selectList(Wrappers.<StdItem>lambdaQuery()
                        .in(StdItem::getAssessmentId, list.stream().map(StdAssessment::getId).toList())
                        .select(StdItem::getAssessmentId, StdItem::getDeduct, StdItem::getNa, StdItem::getHazardId))
                .stream().collect(Collectors.groupingBy(StdItem::getAssessmentId));
        return list.stream().map(a -> {
            List<StdItem> l = items.getOrDefault(a.getId(), List.of());
            return new StdVOs.ListRow(a, (int) l.stream().filter(this::deducted).count(), (int) l.stream().filter(i -> i.getHazardId() != null).count());
        }).toList();
    }

    public StdVOs.Detail detail(Long id) {
        StdAssessment a = assessment(id);
        List<StdItem> items = items(id);
        List<StdRules.Scored> scored = scored(items);
        boolean draft = StdRules.DRAFT.equals(a.getStatus());
        Double score;
        String grade;
        int dead;
        int serious;
        if (draft) {
            int[] perf = evidenceService.performance();
            dead = perf[0];
            serious = perf[1];
            score = StdRules.total(scored);
            grade = StdRules.grade(score, dead, serious);
        } else {
            dead = nz(a.getPerfDead());
            serious = nz(a.getPerfSerious());
            score = a.getScore();
            grade = a.getGrade();
        }
        StdAssessment prev = previous(a);
        List<StdRules.ElementScore> prevElements = prev == null ? null : StdRules.elements(scored(items(prev.getId())));
        List<StdVOs.ItemRow> rows = items.stream().map(i -> new StdVOs.ItemRow(i, parse(i.getEvidence()))).toList();
        int noteMissing = (int) items.stream().filter(i -> deducted(i) && !StringUtils.hasText(i.getNote())).count();
        return new StdVOs.Detail(a, score, grade, dead, serious, StdRules.elements(scored), prev == null ? null : prev.getTitle(),
                prevElements, rows, draft && StpUtil.hasPermission(PERM_EDIT), (int) items.stream().filter(this::deducted).count(), noteMissing);
    }

    /** 新建自评：复制考评项目，系统取证并把建议扣分填进去 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(StdDTOs.Create dto) {
        if (assessmentMapper.selectCount(Wrappers.<StdAssessment>lambdaQuery().eq(StdAssessment::getStatus, StdRules.DRAFT)) > 0) {
            throw new BizException("还有一次自评没有完成，请先完成或删除它");
        }
        SysUser me = userService.getById(StpUtil.getLoginIdAsLong());
        StdAssessment a = new StdAssessment();
        a.setTitle(dto.getTitle().trim());
        a.setYear(LocalDate.now().getYear());
        a.setStatus(StdRules.DRAFT);
        a.setLeaderId(me.getId());
        a.setLeaderName(me.getNickname());
        a.setMembers(StringUtils.hasText(dto.getMembers()) ? dto.getMembers().trim() : null);
        a.setStartDate(LocalDate.now());
        assessmentMapper.insert(a);
        Map<String, StdEvidenceService.Evidence> ev = evidenceService.collect(a.getId());
        int sort = 0;
        for (StdRules.Item t : StdRules.ITEMS) {
            StdItem i = new StdItem();
            i.setAssessmentId(a.getId());
            i.setCode(t.code());
            i.setElement(t.element());
            i.setElementName(StdRules.ELEMENTS.get(t.element() - 1).name());
            i.setTitle(t.title());
            i.setContent(t.content());
            i.setStandardScore(t.score());
            i.setMethod(t.method());
            i.setAuto(t.auto() ? 1 : 0);
            i.setNa(0);
            StdEvidenceService.Evidence e = ev.get(t.code());
            i.setSuggested(e == null ? null : e.suggested());
            i.setDeduct(e == null ? 0.0 : e.suggested());
            i.setEvidence(e == null ? null : write(e.facts()));
            i.setSort(++sort);
            itemMapper.insert(i);
        }
        return a.getId();
    }

    /** 重新取数：只更新证据和建议扣分，评审人已经填的扣分不动 */
    @Transactional(rollbackFor = Exception.class)
    public int refresh(Long id) {
        StdAssessment a = draft(id);
        Map<String, StdEvidenceService.Evidence> ev = evidenceService.collect(a.getId());
        int changed = 0;
        for (StdItem i : items(id)) {
            StdEvidenceService.Evidence e = ev.get(i.getCode());
            if (e == null) {
                continue;
            }
            if (i.getSuggested() == null || Double.compare(i.getSuggested(), e.suggested()) != 0) {
                changed++;
            }
            i.setSuggested(e.suggested());
            i.setEvidence(write(e.facts()));
            itemMapper.updateById(i);
        }
        return changed;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveItem(Long itemId, StdDTOs.ItemSave dto) {
        StdItem i = item(itemId);
        draft(i.getAssessmentId());
        boolean na = Boolean.TRUE.equals(dto.getNa());
        double deduct = na ? 0 : dto.getDeduct();
        String err = StdRules.checkDeduct(i.getStandardScore(), deduct);
        if (err != null) {
            throw new BizException(i.getCode() + " " + err);
        }
        i.setNa(na ? 1 : 0);
        i.setDeduct(deduct);
        i.setNote(StringUtils.hasText(dto.getNote()) ? dto.getNote().trim() : null);
        itemMapper.updateById(i);
    }

    /** 完成自评：扣了分的必须写说明；算得分和等级 */
    @Transactional(rollbackFor = Exception.class)
    public StdVOs.Detail finish(Long id, StdDTOs.Finish dto) {
        StdAssessment a = draft(id);
        List<StdItem> items = items(id);
        StdItem missing = items.stream().filter(i -> deducted(i) && !StringUtils.hasText(i.getNote())).findFirst().orElse(null);
        if (missing != null) {
            throw new BizException(missing.getCode() + "“" + missing.getTitle() + "”扣了 " + fmt(missing.getDeduct()) + " 分，请填写扣分说明");
        }
        Double score = StdRules.total(scored(items));
        if (score == null) {
            throw new BizException("所有项目都是空项，无法计分");
        }
        int[] perf = evidenceService.performance();
        a.setScore(score);
        a.setPerfDead(perf[0]);
        a.setPerfSerious(perf[1]);
        a.setGrade(StdRules.grade(score, perf[0], perf[1]));
        a.setStatus(StdRules.DONE);
        a.setFinishDate(LocalDate.now());
        a.setSummary(dto == null || !StringUtils.hasText(dto.getSummary()) ? null : dto.getSummary().trim());
        assessmentMapper.updateById(a);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        draft(id);
        itemMapper.delete(Wrappers.<StdItem>lambdaQuery().eq(StdItem::getAssessmentId, id));
        assessmentMapper.deleteById(id);
    }

    /** 扣分项转隐患整改（走隐患闭环流程） */
    @Transactional(rollbackFor = Exception.class)
    public StdVOs.HazardResult toHazard(Long itemId) {
        StdItem i = item(itemId);
        StdAssessment a = assessment(i.getAssessmentId());
        if (!deducted(i)) {
            throw new BizException("这一项没有扣分，不需要整改");
        }
        if (i.getHazardId() != null) {
            throw new BizException("已经转过整改了：" + i.getHazardNo());
        }
        HazardReportDTO h = new HazardReportDTO();
        String title = "标准化自评扣分：" + i.getCode() + " " + i.getTitle();
        h.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
        String desc = a.getTitle() + " 第 " + i.getCode() + " 项“" + i.getTitle() + "”扣 " + fmt(i.getDeduct()) + " 分（标准分 " + i.getStandardScore() + "）"
                + "\n扣分说明：" + (StringUtils.hasText(i.getNote()) ? i.getNote() : "无")
                + "\n考评内容：" + i.getContent();
        h.setDescription(desc.length() > 1000 ? desc.substring(0, 1000) : desc);
        h.setCategory("安全管理");
        h.setLevel("GENERAL");
        h.setLocation("全厂（标准化自评）");
        Long hazardId = hazardService.report(h);
        Hazard created = hazardMapper.selectById(hazardId);
        i.setHazardId(hazardId);
        i.setHazardNo(created.getHazardNo());
        itemMapper.updateById(i);
        return new StdVOs.HazardResult(hazardId, created.getHazardNo());
    }

    /** 最近一次已完成的自评（定时任务、大屏、智能体用，不用登录信息） */
    public StdAssessment latestDone() {
        return assessmentMapper.selectOne(Wrappers.<StdAssessment>lambdaQuery().eq(StdAssessment::getStatus, StdRules.DONE)
                .orderByDesc(StdAssessment::getFinishDate).orderByDesc(StdAssessment::getCreateTime).last("LIMIT 1"));
    }

    public StdAssessment currentDraft() {
        return assessmentMapper.selectOne(Wrappers.<StdAssessment>lambdaQuery().eq(StdAssessment::getStatus, StdRules.DRAFT).last("LIMIT 1"));
    }

    /** 扣了分的项目（智能体用） */
    public List<StdItem> deductedItems(Long id) {
        return items(id).stream().filter(this::deducted).toList();
    }

    public void export(Long id, HttpServletResponse response) {
        StdAssessment a = assessment(id);
        List<StdExcelRow> rows = items(id).stream().map(i -> {
            StdExcelRow r = new StdExcelRow();
            r.setCode(i.getCode());
            r.setElement(i.getElement() + " " + i.getElementName());
            r.setTitle(i.getTitle());
            r.setContent(i.getContent());
            r.setStandardScore(i.getStandardScore());
            r.setNa(Integer.valueOf(1).equals(i.getNa()) ? "空项" : "");
            r.setDeduct(Integer.valueOf(1).equals(i.getNa()) ? null : i.getDeduct());
            r.setGot(Integer.valueOf(1).equals(i.getNa()) ? null : i.getStandardScore() - Math.min(i.getStandardScore(), nzd(i.getDeduct())));
            r.setNote(i.getNote());
            r.setHazardNo(i.getHazardNo());
            return r;
        }).toList();
        ExcelHelper.write(response, a.getTitle() + "（扣分汇总）", StdExcelRow.class, rows);
    }

    // ============================== 内部 ==============================

    private StdAssessment previous(StdAssessment a) {
        return assessmentMapper.selectOne(Wrappers.<StdAssessment>lambdaQuery().eq(StdAssessment::getStatus, StdRules.DONE)
                .ne(StdAssessment::getId, a.getId())
                .le(a.getFinishDate() != null, StdAssessment::getFinishDate, a.getFinishDate())
                .orderByDesc(StdAssessment::getFinishDate).orderByDesc(StdAssessment::getCreateTime).last("LIMIT 1"));
    }

    private StdAssessment assessment(Long id) {
        StdAssessment a = assessmentMapper.selectById(id);
        if (a == null) {
            throw new BizException(404, "自评不存在");
        }
        return a;
    }

    private StdAssessment draft(Long id) {
        StdAssessment a = assessment(id);
        if (!StdRules.DRAFT.equals(a.getStatus())) {
            throw new BizException("自评已经完成，不能再修改");
        }
        return a;
    }

    private StdItem item(Long id) {
        StdItem i = itemMapper.selectById(id);
        if (i == null) {
            throw new BizException(404, "考评项目不存在");
        }
        return i;
    }

    private List<StdItem> items(Long assessmentId) {
        return itemMapper.selectList(Wrappers.<StdItem>lambdaQuery().eq(StdItem::getAssessmentId, assessmentId).orderByAsc(StdItem::getSort));
    }

    private static List<StdRules.Scored> scored(List<StdItem> items) {
        return items.stream().map(i -> new StdRules.Scored(i.getCode(), i.getElement(), i.getStandardScore(),
                Integer.valueOf(1).equals(i.getNa()), nzd(i.getDeduct()))).toList();
    }

    private boolean deducted(StdItem i) {
        return !Integer.valueOf(1).equals(i.getNa()) && nzd(i.getDeduct()) > 0;
    }

    private List<String> parse(String s) {
        if (!StringUtils.hasText(s)) {
            return List.of();
        }
        try {
            return json.readValue(s, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of(s);
        }
    }

    private String write(List<String> facts) {
        try {
            return json.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static double nzd(Double v) {
        return v == null ? 0 : v;
    }

    static String fmt(Double v) {
        if (v == null) {
            return "-";
        }
        return v == Math.floor(v) ? String.valueOf(v.longValue()) : String.valueOf(v);
    }
}
