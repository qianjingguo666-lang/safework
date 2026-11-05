package com.safework.safety.ppe;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.PpeIssue;
import com.safework.safety.entity.PpeItem;
import com.safework.safety.entity.PpePost;
import com.safework.safety.entity.PpePostUser;
import com.safework.safety.entity.PpeStandard;
import com.safework.safety.mapper.PpeIssueMapper;
import com.safework.safety.mapper.PpeItemMapper;
import com.safework.safety.mapper.PpePostMapper;
import com.safework.safety.mapper.PpePostUserMapper;
import com.safework.safety.mapper.PpeStandardMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 劳保用品：用品目录（库存）→ 岗位发放标准（岗位 × 用品 × 数量）→ 发放登记 → 台账（每人应配的每种用品：未发放 / 已过期 / 快到期 / 正常）。
 * <p>
 * 台账不单独存表，每次按“岗位标准 + 最新的在用发放记录”算出来：改了岗位标准、人员调岗，台账立即反映，不会和实际不一致。
 * 一个人在多个岗位时，同一种用品取数量多的那个标准。
 * <p>
 * 数据权限：有 ppe:edit 的安全员管理目录、岗位、发放，看全部；其他人只看自己的。
 */
@Service
@RequiredArgsConstructor
public class PpeService {

    public static final String PERM_LIST = "ppe:list";
    public static final String PERM_EDIT = "ppe:edit";
    static final String LINK = "/safety/ppe";

    private final PpeItemMapper itemMapper;
    private final PpePostMapper postMapper;
    private final PpePostUserMapper postUserMapper;
    private final PpeStandardMapper standardMapper;
    private final PpeIssueMapper issueMapper;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 用品目录 ==============================

    public List<PpeItem> items(Boolean enabledOnly) {
        return itemMapper.selectList(Wrappers.<PpeItem>lambdaQuery()
                .eq(Boolean.TRUE.equals(enabledOnly), PpeItem::getStatus, 1)
                .orderByDesc(PpeItem::getStatus)
                .orderByAsc(PpeItem::getCategory)
                .orderByAsc(PpeItem::getName));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveItem(Long id, PpeDTOs.ItemSave dto) {
        if (!PpeRules.CATEGORIES.containsKey(dto.getCategory())) {
            throw new BizException("防护部位不正确");
        }
        String error = PpeRules.checkMonths(dto.getValidMonths());
        if (error != null) {
            throw new BizException(error);
        }
        String name = dto.getName().trim();
        String spec = trim(dto.getSpec());
        if (itemMapper.selectCount(Wrappers.<PpeItem>lambdaQuery()
                .eq(PpeItem::getName, name)
                .eq(spec != null, PpeItem::getSpec, spec)
                .isNull(spec == null, PpeItem::getSpec)
                .ne(id != null, PpeItem::getId, id)) > 0) {
            throw new BizException("已经有【" + name + (spec == null ? "" : " " + spec) + "】了");
        }
        PpeItem it = id == null ? new PpeItem() : loadItem(id);
        it.setName(name);
        it.setCategory(dto.getCategory());
        it.setSpec(spec);
        it.setUnit(dto.getUnit().trim());
        it.setValidMonths(dto.getValidMonths());
        it.setMinStock(dto.getMinStock());
        it.setRemark(trim(dto.getRemark()));
        if (id == null) {
            it.setStock(dto.getStock() == null ? 0 : dto.getStock());
            it.setStatus(1);
            itemMapper.insert(it);
        } else {
            itemMapper.updateById(it);
        }
        return it.getId();
    }

    /** 入库：库存直接在数据库里加，两个人同时入库也不会丢数 */
    @Transactional(rollbackFor = Exception.class)
    public int stockIn(Long id, int quantity) {
        loadItem(id);
        itemMapper.update(null, Wrappers.<PpeItem>lambdaUpdate().setSql("stock = stock + " + quantity).eq(PpeItem::getId, id));
        return loadItem(id).getStock();
    }

    @Transactional(rollbackFor = Exception.class)
    public void itemStatus(Long id, int status) {
        PpeItem it = loadItem(id);
        if (status == 0) {
            long used = standardMapper.selectCount(Wrappers.<PpeStandard>lambdaQuery().eq(PpeStandard::getItemId, id));
            if (used > 0) {
                throw new BizException("有 " + used + " 个岗位的发放标准里有【" + it.getName() + "】，先从岗位标准里去掉");
            }
        }
        it.setStatus(status == 1 ? 1 : 0);
        itemMapper.updateById(it);
    }

    // ============================== 岗位和发放标准 ==============================

    public List<PpeVOs.Post> posts() {
        List<PpePost> posts = postMapper.selectList(Wrappers.<PpePost>lambdaQuery()
                .orderByDesc(PpePost::getStatus).orderByAsc(PpePost::getName));
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PpePostUser>> members = postUserMapper.selectList(Wrappers.<PpePostUser>lambdaQuery()
                        .orderByAsc(PpePostUser::getUserName))
                .stream().collect(Collectors.groupingBy(PpePostUser::getPostId));
        Map<Long, List<PpeStandard>> standards = standardMapper.selectList(Wrappers.<PpeStandard>lambdaQuery())
                .stream().collect(Collectors.groupingBy(PpeStandard::getPostId));
        Map<Long, PpeItem> items = itemMap();
        return posts.stream().map(p -> new PpeVOs.Post(p.getId(), p.getName(), p.getRemark(), p.getStatus(),
                members.getOrDefault(p.getId(), List.of()).stream().map(m -> new PpeVOs.Member(m.getUserId(), m.getUserName())).toList(),
                standards.getOrDefault(p.getId(), List.of()).stream()
                        .filter(s -> items.containsKey(s.getItemId()))
                        .map(s -> {
                            PpeItem it = items.get(s.getItemId());
                            return new PpeVOs.Standard(it.getId(), itemLabel(it), it.getUnit(), it.getValidMonths(), s.getQuantity());
                        }).toList()))
                .toList();
    }

    /** 新建 / 修改岗位：人员和发放标准整体替换 */
    @Transactional(rollbackFor = Exception.class)
    public Long savePost(Long id, PpeDTOs.PostSave dto) {
        String name = dto.getName().trim();
        if (postMapper.selectCount(Wrappers.<PpePost>lambdaQuery()
                .eq(PpePost::getName, name).ne(id != null, PpePost::getId, id)) > 0) {
            throw new BizException("已经有【" + name + "】这个岗位了");
        }
        Set<Long> itemIds = new HashSet<>();
        Map<Long, PpeItem> items = itemMap();
        for (PpeDTOs.StandardItem s : dto.getStandards()) {
            PpeItem it = items.get(s.getItemId());
            if (it == null) {
                throw new BizException("劳保用品不存在");
            }
            if (!Integer.valueOf(1).equals(it.getStatus())) {
                throw new BizException("【" + it.getName() + "】已停用");
            }
            if (!itemIds.add(s.getItemId())) {
                throw new BizException("【" + it.getName() + "】重复了");
            }
        }
        List<SysUser> users = new ArrayList<>();
        for (Long uid : new LinkedHashSet<>(dto.getUserIds() == null ? List.<Long>of() : dto.getUserIds())) {
            SysUser u = userService.getById(uid);
            if (!Integer.valueOf(1).equals(u.getStatus())) {
                throw new BizException("【" + u.getNickname() + "】的账号已停用");
            }
            users.add(u);
        }
        PpePost p = id == null ? new PpePost() : loadPost(id);
        p.setName(name);
        p.setRemark(trim(dto.getRemark()));
        if (id == null) {
            p.setStatus(1);
            postMapper.insert(p);
        } else {
            postMapper.updateById(p);
            postUserMapper.delete(Wrappers.<PpePostUser>lambdaQuery().eq(PpePostUser::getPostId, id));
            standardMapper.delete(Wrappers.<PpeStandard>lambdaQuery().eq(PpeStandard::getPostId, id));
        }
        for (SysUser u : users) {
            PpePostUser m = new PpePostUser();
            m.setPostId(p.getId());
            m.setUserId(u.getId());
            m.setUserName(u.getNickname());
            postUserMapper.insert(m);
        }
        for (PpeDTOs.StandardItem s : dto.getStandards()) {
            PpeStandard st = new PpeStandard();
            st.setPostId(p.getId());
            st.setItemId(s.getItemId());
            st.setQuantity(s.getQuantity());
            standardMapper.insert(st);
        }
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void postStatus(Long id, int status) {
        PpePost p = loadPost(id);
        p.setStatus(status == 1 ? 1 : 0);
        postMapper.updateById(p);
    }

    // ============================== 台账 ==============================

    /** 台账：安全员看全部，其他人只看自己的 */
    public PpeVOs.LedgerPage ledger(PpeDTOs.LedgerQuery q) {
        Long only = StpUtil.hasPermission(PERM_EDIT) ? null : StpUtil.getLoginIdAsLong();
        List<PpeVOs.LedgerRow> rows = compute(only, q.getPostId(), LocalDate.now());
        String kw = q.getKeyword();
        String state = q.getState();
        List<PpeVOs.LedgerRow> list = rows.stream()
                .filter(r -> !StringUtils.hasText(kw) || r.userName().contains(kw) || r.itemName().contains(kw) || r.posts().contains(kw))
                .filter(r -> !StringUtils.hasText(state) || ("NEED".equals(state) ? PpeRules.needIssue(r.state()) : r.state().equals(state)))
                .toList();
        long from = Math.max(0, (q.getCurrent() - 1) * q.getSize());
        long to = Math.min(list.size(), from + q.getSize());
        return new PpeVOs.LedgerPage(from >= list.size() ? List.of() : list.subList((int) from, (int) to), list.size());
    }

    /** 我自己的（手机端“我的劳保”、首页待办），不看权限 */
    public List<PpeVOs.LedgerRow> mine() {
        return compute(StpUtil.getLoginIdAsLong(), null, LocalDate.now());
    }

    public PpeVOs.Overview overview() {
        Long only = StpUtil.hasPermission(PERM_EDIT) ? null : StpUtil.getLoginIdAsLong();
        List<PpeVOs.LedgerRow> rows = compute(only, null, LocalDate.now());
        int lowStock = Math.toIntExact(itemMapper.selectCount(Wrappers.<PpeItem>lambdaQuery()
                .eq(PpeItem::getStatus, 1).apply("stock < min_stock")));
        return new PpeVOs.Overview((int) rows.stream().map(PpeVOs.LedgerRow::userId).distinct().count(),
                count(rows, PpeRules.MISSING), count(rows, PpeRules.EXPIRED), count(rows, PpeRules.DUE_SOON), lowStock);
    }

    /**
     * 算台账：岗位标准（谁应该有什么）对比最新的在用发放记录。定时任务也调用（没有登录信息，userId 传空看全部）。
     * 排序：要处理的在前（已过期、未发放、快到期、正常），同一状态按人、用品排。
     */
    public List<PpeVOs.LedgerRow> compute(Long userId, Long postId, LocalDate today) {
        Map<Long, PpePost> posts = postMapper.selectList(Wrappers.<PpePost>lambdaQuery().eq(PpePost::getStatus, 1))
                .stream().collect(Collectors.toMap(PpePost::getId, Function.identity()));
        if (posts.isEmpty()) {
            return List.of();
        }
        List<PpePostUser> members = postUserMapper.selectList(Wrappers.<PpePostUser>lambdaQuery()
                .in(PpePostUser::getPostId, posts.keySet())
                .eq(postId != null, PpePostUser::getPostId, postId)
                .eq(userId != null, PpePostUser::getUserId, userId));
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PpeStandard>> standards = standardMapper.selectList(Wrappers.<PpeStandard>lambdaQuery()
                        .in(PpeStandard::getPostId, members.stream().map(PpePostUser::getPostId).collect(Collectors.toSet())))
                .stream().collect(Collectors.groupingBy(PpeStandard::getPostId));
        Map<Long, PpeItem> items = itemMap();

        // 每个人：应配的用品（多个岗位取数量多的）、所在岗位名称
        Map<Long, String> names = new HashMap<>();
        Map<Long, Map<Long, Integer>> need = new TreeMap<>();
        Map<Long, Set<String>> postNames = new HashMap<>();
        for (PpePostUser m : members) {
            names.put(m.getUserId(), m.getUserName());
            postNames.computeIfAbsent(m.getUserId(), k -> new LinkedHashSet<>()).add(posts.get(m.getPostId()).getName());
            for (PpeStandard s : standards.getOrDefault(m.getPostId(), List.of())) {
                if (items.containsKey(s.getItemId())) {
                    need.computeIfAbsent(m.getUserId(), k -> new LinkedHashMap<>()).merge(s.getItemId(), s.getQuantity(), Math::max);
                }
            }
        }
        if (need.isEmpty()) {
            return List.of();
        }
        // 每人每种用品最新的一条在用记录
        Map<String, PpeIssue> latest = new HashMap<>();
        issueMapper.selectList(Wrappers.<PpeIssue>lambdaQuery()
                        .eq(PpeIssue::getStatus, PpeRules.IN_USE)
                        .in(PpeIssue::getUserId, need.keySet()))
                .forEach(i -> latest.merge(i.getUserId() + ":" + i.getItemId(), i,
                        (a, b) -> a.getIssueDate().isAfter(b.getIssueDate()) ? a : b));

        List<PpeVOs.LedgerRow> rows = new ArrayList<>();
        need.forEach((uid, itemQty) -> itemQty.forEach((itemId, qty) -> {
            PpeItem it = items.get(itemId);
            PpeIssue last = latest.get(uid + ":" + itemId);
            LocalDate expire = last == null ? null : last.getExpireDate();
            rows.add(new PpeVOs.LedgerRow(uid, names.get(uid), String.join("、", postNames.get(uid)), itemId, itemLabel(it),
                    it.getCategory(), it.getUnit(), qty, PpeRules.state(expire, today),
                    last == null ? null : last.getIssueDate(), expire,
                    expire == null ? null : ChronoUnit.DAYS.between(today, expire)));
        }));
        List<String> order = List.of(PpeRules.EXPIRED, PpeRules.MISSING, PpeRules.DUE_SOON, PpeRules.OK);
        rows.sort(Comparator.comparing((PpeVOs.LedgerRow r) -> order.indexOf(r.state()))
                .thenComparing(PpeVOs.LedgerRow::userName)
                .thenComparing(PpeVOs.LedgerRow::itemName));
        return rows;
    }

    // ============================== 发放 ==============================

    /**
     * 发放登记：扣库存（数据库里 stock >= 数量 才扣，库存不够整单失败）、同一种用品之前的在用记录改成“已更换”、通知领用人。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> issue(PpeDTOs.Issue dto) {
        SysUser user = userService.getById(dto.getUserId());
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException("【" + user.getNickname() + "】的账号已停用");
        }
        LocalDate today = LocalDate.now();
        LocalDate date = dto.getIssueDate() == null ? today : dto.getIssueDate();
        if (date.isAfter(today)) {
            throw new BizException("发放日期不能晚于今天");
        }
        if (date.isBefore(today.minusYears(1))) {
            throw new BizException("发放日期不能早于一年前");
        }
        Set<Long> seen = new HashSet<>();
        for (PpeDTOs.StandardItem s : dto.getItems()) {
            if (!seen.add(s.getItemId())) {
                throw new BizException("同一种用品请合并成一行");
            }
        }
        String issuer = userService.getById(StpUtil.getLoginIdAsLong()).getNickname();
        List<Long> ids = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (PpeDTOs.StandardItem s : dto.getItems()) {
            PpeItem it = loadItem(s.getItemId());
            ids.add(doIssue(user.getId(), user.getNickname(), it, s.getQuantity(), date, trim(dto.getRemark()), issuer));
            lines.add(itemLabel(it) + " × " + s.getQuantity() + it.getUnit() + "（" + PpeRules.expire(date, it.getValidMonths()) + " 到期）");
        }
        notifyUser(user.getId(), lines);
        return ids;
    }

    /**
     * 批量补发：给某个岗位（不传就是全部岗位）所有“未发放、已过期”的人发放。
     * 先按用品汇总需求和库存比较，有任何一种不够就一件都不发，并说明缺多少。
     */
    @Transactional(rollbackFor = Exception.class)
    public PpeVOs.BatchResult batchIssue(Long postId) {
        LocalDate today = LocalDate.now();
        List<PpeVOs.LedgerRow> todo = compute(null, postId, today).stream().filter(r -> PpeRules.needIssue(r.state())).toList();
        if (todo.isEmpty()) {
            throw new BizException("没有需要补发的（未发放、已过期的都已经发了）");
        }
        Map<Long, PpeItem> items = itemMap();
        Map<Long, Integer> need = new LinkedHashMap<>();
        todo.forEach(r -> need.merge(r.itemId(), r.quantity(), Integer::sum));
        List<String> shortage = PpeRules.shortage(need,
                items.values().stream().collect(Collectors.toMap(PpeItem::getId, PpeItem::getStock)),
                items.values().stream().collect(Collectors.toMap(PpeItem::getId, PpeService::itemLabel)));
        if (!shortage.isEmpty()) {
            throw new BizException("库存不够，没有发放：" + String.join("；", shortage) + "。请先入库");
        }
        String issuer = userService.getById(StpUtil.getLoginIdAsLong()).getNickname();
        Map<Long, List<String>> byUser = new LinkedHashMap<>();
        for (PpeVOs.LedgerRow r : todo) {
            PpeItem it = items.get(r.itemId());
            doIssue(r.userId(), r.userName(), it, r.quantity(), today, "批量补发", issuer);
            byUser.computeIfAbsent(r.userId(), k -> new ArrayList<>())
                    .add(itemLabel(it) + " × " + r.quantity() + it.getUnit() + "（" + PpeRules.expire(today, it.getValidMonths()) + " 到期）");
        }
        byUser.forEach(this::notifyUser);
        List<String> lines = todo.stream().map(r -> r.userName() + "：" + r.itemName() + " × " + r.quantity()).toList();
        return new PpeVOs.BatchResult(byUser.size(), todo.size(), lines);
    }

    public Page<PpeIssue> issuePage(PpeDTOs.IssueQuery q) {
        boolean all = StpUtil.hasPermission(PERM_EDIT);
        String kw = q.getKeyword();
        LambdaQueryWrapper<PpeIssue> w = Wrappers.<PpeIssue>lambdaQuery()
                .eq(!all, PpeIssue::getUserId, StpUtil.getLoginIdAsLong())
                .eq(q.getItemId() != null, PpeIssue::getItemId, q.getItemId())
                .eq(StringUtils.hasText(q.getStatus()), PpeIssue::getStatus, q.getStatus())
                .and(StringUtils.hasText(kw), x -> x.like(PpeIssue::getUserName, kw).or().like(PpeIssue::getItemName, kw))
                .orderByDesc(PpeIssue::getIssueDate)
                .orderByDesc(PpeIssue::getCreateTime);
        return issueMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), w);
    }

    // ============================== 内部方法 ==============================

    private Long doIssue(Long userId, String userName, PpeItem it, int quantity, LocalDate date, String remark, String issuer) {
        if (!Integer.valueOf(1).equals(it.getStatus())) {
            throw new BizException("【" + it.getName() + "】已停用");
        }
        // 扣库存：条件更新，不会扣成负数；并发发放时后到的人看到库存不足
        int updated = itemMapper.update(null, Wrappers.<PpeItem>lambdaUpdate()
                .setSql("stock = stock - " + quantity)
                .eq(PpeItem::getId, it.getId())
                .ge(PpeItem::getStock, quantity));
        if (updated == 0) {
            throw new BizException("【" + itemLabel(it) + "】库存不足（现有 " + loadItem(it.getId()).getStock() + "），请先入库");
        }
        it.setStock(it.getStock() - quantity);
        // 之前发的同一种用品换下来了
        issueMapper.update(null, Wrappers.<PpeIssue>lambdaUpdate()
                .set(PpeIssue::getStatus, PpeRules.REPLACED)
                .eq(PpeIssue::getUserId, userId)
                .eq(PpeIssue::getItemId, it.getId())
                .eq(PpeIssue::getStatus, PpeRules.IN_USE));
        PpeIssue r = new PpeIssue();
        r.setUserId(userId);
        r.setUserName(userName);
        r.setItemId(it.getId());
        r.setItemName(itemLabel(it));
        r.setQuantity(quantity);
        r.setIssueDate(date);
        r.setExpireDate(PpeRules.expire(date, it.getValidMonths()));
        r.setStatus(PpeRules.IN_USE);
        r.setIssuerName(issuer);
        r.setRemark(remark);
        issueMapper.insert(r);
        return r.getId();
    }

    private void notifyUser(Long userId, List<String> lines) {
        messageService.send(List.of(userId), MessageDraft.of(MessageCategory.NOTICE, "PPE", null,
                "已为你发放劳保用品 " + lines.size() + " 种", String.join("；", lines) + "。请按规定佩戴，到期前会提醒你更换", LINK));
    }

    static String itemLabel(PpeItem it) {
        return it.getName() + (StringUtils.hasText(it.getSpec()) ? "（" + it.getSpec() + "）" : "");
    }

    private Map<Long, PpeItem> itemMap() {
        return itemMapper.selectList(Wrappers.<PpeItem>lambdaQuery())
                .stream().collect(Collectors.toMap(PpeItem::getId, Function.identity()));
    }

    private static int count(List<PpeVOs.LedgerRow> rows, String state) {
        return (int) rows.stream().filter(r -> Objects.equals(r.state(), state)).count();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private PpeItem loadItem(Long id) {
        PpeItem it = itemMapper.selectById(id);
        if (it == null) {
            throw new BizException(404, "劳保用品不存在");
        }
        return it;
    }

    private PpePost loadPost(Long id) {
        PpePost p = postMapper.selectById(id);
        if (p == null) {
            throw new BizException(404, "岗位不存在");
        }
        return p;
    }
}
