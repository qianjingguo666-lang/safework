package com.safework.safety.chem;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.ChemFlow;
import com.safework.safety.entity.ChemItem;
import com.safework.safety.entity.ChemStock;
import com.safework.safety.entity.ChemStore;
import com.safework.safety.mapper.ChemFlowMapper;
import com.safework.safety.mapper.ChemItemMapper;
import com.safework.safety.mapper.ChemStockMapper;
import com.safework.safety.mapper.ChemStoreMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.file.SysFile;
import com.safework.system.mapper.SysFileMapper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 危险化学品管理：
 * <pre>
 * 危化品目录（CAS、危险类别、临界量 Q、校正系数 β、MSDS 附件）
 *      │
 * 存储单元（罐区 / 仓库 / 装置，500 m 内人数）── 存放的危化品（设计最大储量、当前库存）
 *      │                                              │ 出库 / 入库 / 盘点（单元负责人或管理员），流水只增不改
 *      ▼                                              ▼
 * 重大危险源辨识：S = Σ q/Q ≥ 1 构成；R = α Σ β q/Q 分一 ~ 四级       超过最大储量 → 提醒管理员
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ChemService {

    public static final String PERM_LIST = "chem:list";
    public static final String PERM_EDIT = "chem:edit";
    public static final String BIZ_TYPE = "CHEM";
    public static final String TAG_MSDS = "MSDS";
    public static final String LINK = "/safety/chem";

    private final ChemItemMapper itemMapper;
    private final ChemStoreMapper storeMapper;
    private final ChemStockMapper stockMapper;
    private final ChemFlowMapper flowMapper;
    private final SysFileMapper fileMapper;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 目录 ==============================

    public List<ChemVOs.ItemRow> items(String keyword, boolean enabledOnly) {
        List<ChemItem> items = itemMapper.selectList(Wrappers.<ChemItem>lambdaQuery()
                .eq(enabledOnly, ChemItem::getStatus, 1)
                .and(StringUtils.hasText(keyword), w -> w.like(ChemItem::getName, keyword).or().like(ChemItem::getCas, keyword))
                .orderByDesc(ChemItem::getStatus).orderByAsc(ChemItem::getName));
        Map<Long, List<ChemStock>> stocks = stockMapper.selectList(Wrappers.<ChemStock>lambdaQuery())
                .stream().collect(Collectors.groupingBy(ChemStock::getChemId));
        Set<Long> msds = msdsOf(items.stream().map(ChemItem::getId).toList());
        return items.stream().map(i -> {
            List<ChemStock> l = stocks.getOrDefault(i.getId(), List.of());
            return new ChemVOs.ItemRow(i, l.size(), l.stream().map(ChemStock::getQty).reduce(BigDecimal.ZERO, BigDecimal::add), msds.contains(i.getId()));
        }).toList();
    }

    public ChemItem item(Long id) {
        ChemItem i = itemMapper.selectById(id);
        if (i == null) {
            throw new BizException(404, "危化品不存在");
        }
        return i;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveItem(Long id, ChemDTOs.ItemSave dto) {
        if (!ChemRules.CATEGORIES.containsKey(dto.getCategory())) {
            throw new BizException("危险类别不正确");
        }
        String cas = dto.getCas().trim();
        if (!ChemRules.validCas(cas)) {
            throw new BizException("CAS 号格式不正确或校验位不对（例如甲醇是 67-56-1）");
        }
        ChemItem dup = itemMapper.selectOne(Wrappers.<ChemItem>lambdaQuery().eq(ChemItem::getCas, cas).last("LIMIT 1"));
        if (dup != null && !dup.getId().equals(id)) {
            throw new BizException("CAS 号 " + cas + " 已经在目录里了（" + dup.getName() + "）");
        }
        ChemItem i = id == null ? new ChemItem() : item(id);
        i.setName(dto.getName().trim());
        i.setCas(cas);
        i.setUnNo(trim(dto.getUnNo()));
        i.setCategory(dto.getCategory());
        i.setHazards(trim(dto.getHazards()));
        i.setCriticalQty(dto.getCriticalQty());
        i.setBeta(dto.getBeta() != null ? dto.getBeta() : ChemRules.defaultBeta(dto.getCategory()));
        i.setRemark(trim(dto.getRemark()));
        if (id == null) {
            i.setStatus(1);
            itemMapper.insert(i);
        } else {
            itemMapper.updateById(i);
        }
        return i.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void itemStatus(Long id, int status) {
        ChemItem i = item(id);
        if (status == 0 && stockMapper.selectList(Wrappers.<ChemStock>lambdaQuery().eq(ChemStock::getChemId, id))
                .stream().anyMatch(s -> s.getQty().signum() > 0)) {
            throw new BizException("还有库存，不能停用（先出库或盘点为 0）");
        }
        i.setStatus(status == 1 ? 1 : 0);
        itemMapper.updateById(i);
    }

    // ============================== 存储单元和重大危险源 ==============================

    public List<ChemVOs.Store> stores() {
        List<ChemStore> stores = storeMapper.selectList(Wrappers.<ChemStore>lambdaQuery()
                .orderByDesc(ChemStore::getStatus).orderByAsc(ChemStore::getName));
        return build(stores, true);
    }

    public ChemVOs.Store store(Long id) {
        return build(List.of(loadStore(id)), true).get(0);
    }

    /** 我负责的单元（手机端出入库用）；管理员看全部启用的 */
    public List<ChemVOs.Store> myStores() {
        LambdaQueryWrapper<ChemStore> w = Wrappers.<ChemStore>lambdaQuery().eq(ChemStore::getStatus, 1).orderByAsc(ChemStore::getName);
        if (!StpUtil.hasPermission(PERM_EDIT)) {
            w.eq(ChemStore::getManagerId, StpUtil.getLoginIdAsLong());
        }
        return build(storeMapper.selectList(w), true);
    }

    /** 手机首页：我负责的单元里超储的危化品种数；不是负责人返回 null */
    public Integer myOverstock() {
        List<ChemStore> mine = storeMapper.selectList(Wrappers.<ChemStore>lambdaQuery()
                .eq(ChemStore::getStatus, 1).eq(ChemStore::getManagerId, StpUtil.getLoginIdAsLong()));
        if (mine.isEmpty()) {
            return null;
        }
        return (int) stockMapper.selectList(Wrappers.<ChemStock>lambdaQuery().in(ChemStock::getStoreId, mine.stream().map(ChemStore::getId).toList()))
                .stream().filter(s -> ChemRules.overstock(s.getQty(), s.getMaxQty())).count();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveStore(Long id, ChemDTOs.StoreSave dto) {
        if (!ChemRules.STORE_TYPES.containsKey(dto.getStoreType())) {
            throw new BizException("单元类型不正确");
        }
        SysUser m = userService.getById(dto.getManagerId());
        if (m == null || !Integer.valueOf(1).equals(m.getStatus())) {
            throw new BizException("负责人不存在或账号已停用");
        }
        ChemStore s = id == null ? new ChemStore() : loadStore(id);
        s.setName(dto.getName().trim());
        s.setStoreType(dto.getStoreType());
        s.setLocation(dto.getLocation().trim());
        s.setPeople(dto.getPeople());
        s.setManagerId(m.getId());
        s.setManagerName(m.getNickname());
        s.setRemark(trim(dto.getRemark()));
        if (id == null) {
            s.setStatus(1);
            storeMapper.insert(s);
        } else {
            storeMapper.updateById(s);
        }
        return s.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void storeStatus(Long id, int status) {
        ChemStore s = loadStore(id);
        if (status == 0 && stockMapper.selectList(Wrappers.<ChemStock>lambdaQuery().eq(ChemStock::getStoreId, id))
                .stream().anyMatch(x -> x.getQty().signum() > 0)) {
            throw new BizException("单元里还有库存，不能停用");
        }
        s.setStatus(status == 1 ? 1 : 0);
        storeMapper.updateById(s);
    }

    /** 单元里新增一种危化品，或修改它的设计最大储量 */
    @Transactional(rollbackFor = Exception.class)
    public Long saveStock(Long storeId, ChemDTOs.StockSave dto) {
        loadStore(storeId);
        ChemItem i = item(dto.getChemId());
        if (!Integer.valueOf(1).equals(i.getStatus())) {
            throw new BizException("【" + i.getName() + "】已停用");
        }
        ChemStock s = stockMapper.selectOne(Wrappers.<ChemStock>lambdaQuery()
                .eq(ChemStock::getStoreId, storeId).eq(ChemStock::getChemId, i.getId()));
        if (s == null) {
            s = new ChemStock();
            s.setStoreId(storeId);
            s.setChemId(i.getId());
            s.setQty(BigDecimal.ZERO);
            s.setMaxQty(dto.getMaxQty());
            stockMapper.insert(s);
        } else {
            ChemStock patch = new ChemStock();
            patch.setId(s.getId());
            patch.setMaxQty(dto.getMaxQty());
            stockMapper.updateById(patch);
        }
        return s.getId();
    }

    /** 从单元里去掉一种危化品：库存必须是 0 */
    @Transactional(rollbackFor = Exception.class)
    public void removeStock(Long stockId) {
        ChemStock s = loadStock(stockId);
        if (s.getQty().signum() > 0) {
            throw new BizException("还有库存，不能去掉（先出库或盘点为 0）");
        }
        stockMapper.deleteById(stockId);
    }

    // ============================== 出入库 ==============================

    /**
     * 出库 / 入库 / 盘点。库存按“变动前的值”条件更新，并发时后提交的失败；
     * 入库后超过设计最大储量照样入（东西已经到了），但标出来并提醒管理员。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChemVOs.FlowResult flow(Long stockId, ChemDTOs.Flow dto) {
        ChemStock s = loadStock(stockId);
        ChemStore store = loadStore(s.getStoreId());
        if (!canFlow(store)) {
            throw new BizException(403, "只有单元负责人和安全管理员能办理出入库");
        }
        if (!Integer.valueOf(1).equals(store.getStatus())) {
            throw new BizException("单元已停用");
        }
        if (!ChemRules.FLOW_TYPES.containsKey(dto.getFlowType())) {
            throw new BizException("类型不正确");
        }
        if (!ChemRules.IN.equals(dto.getFlowType()) && !StringUtils.hasText(dto.getPurpose())) {
            throw new BizException(ChemRules.OUT.equals(dto.getFlowType()) ? "请填写领用用途" : "请填写盘点说明");
        }
        ChemItem i = item(s.getChemId());
        BigDecimal qty = dto.getQty().setScale(3, java.math.RoundingMode.HALF_UP);
        BigDecimal after;
        try {
            after = ChemRules.apply(dto.getFlowType(), s.getQty(), qty);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        if (stockMapper.changeQty(s.getId(), s.getQty(), after) == 0) {
            throw new BizException("库存刚被别人改过，请刷新后再试");
        }
        SysUser me = userService.getById(StpUtil.getLoginIdAsLong());
        ChemFlow f = new ChemFlow();
        f.setStockId(s.getId());
        f.setStoreId(store.getId());
        f.setStoreName(store.getName());
        f.setChemId(i.getId());
        f.setChemName(i.getName());
        f.setFlowType(dto.getFlowType());
        f.setQty(ChemRules.CHECK.equals(dto.getFlowType()) ? after.subtract(s.getQty()) : qty);
        f.setBalance(after);
        f.setPurpose(trim(dto.getPurpose()));
        f.setOperatorId(me.getId());
        f.setOperatorName(me.getNickname());
        flowMapper.insert(f);
        boolean over = ChemRules.overstock(after, s.getMaxQty());
        if (over && !ChemRules.overstock(s.getQty(), s.getMaxQty())) {
            List<Long> to = new ArrayList<>(messageService.listPermissionUsers(PERM_EDIT));
            to.remove(me.getId());
            if (!to.isEmpty()) {
                messageService.send(to, MessageDraft.of(MessageCategory.WARN, BIZ_TYPE, store.getId(),
                        "危化品超储：" + store.getName() + " " + i.getName(),
                        "当前 " + plain(after) + " 吨，超过设计最大储量 " + plain(s.getMaxQty()) + " 吨（" + me.getNickname() + " 入库后）", LINK));
            }
        }
        return new ChemVOs.FlowResult(after, over);
    }

    public ChemVOs.FlowPage flows(ChemDTOs.FlowQuery q) {
        LambdaQueryWrapper<ChemFlow> w = Wrappers.<ChemFlow>lambdaQuery()
                .eq(q.getStoreId() != null, ChemFlow::getStoreId, q.getStoreId())
                .eq(q.getChemId() != null, ChemFlow::getChemId, q.getChemId())
                .eq(StringUtils.hasText(q.getFlowType()), ChemFlow::getFlowType, q.getFlowType())
                .ge(q.getFrom() != null, ChemFlow::getCreateTime, q.getFrom() == null ? null : q.getFrom().atStartOfDay())
                .lt(q.getTo() != null, ChemFlow::getCreateTime, q.getTo() == null ? null : q.getTo().plusDays(1).atStartOfDay())
                .orderByDesc(ChemFlow::getCreateTime).orderByDesc(ChemFlow::getId);
        Page<ChemFlow> page = flowMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), w);
        return new ChemVOs.FlowPage(page.getRecords(), page.getTotal());
    }

    public ChemVOs.Overview overview() {
        List<ChemVOs.Store> stores = stores().stream().filter(s -> Integer.valueOf(1).equals(s.store().getStatus())).toList();
        List<ChemItem> items = itemMapper.selectList(Wrappers.<ChemItem>lambdaQuery().eq(ChemItem::getStatus, 1));
        Set<Long> msds = msdsOf(items.stream().map(ChemItem::getId).toList());
        int[] lv = new int[5];
        stores.forEach(s -> {
            if (s.design().level() != null) {
                lv[s.design().level()]++;
            }
        });
        return new ChemVOs.Overview(items.size(), stores.size(), lv[1] + lv[2] + lv[3] + lv[4], lv[1], lv[2], lv[3], lv[4],
                stores.stream().mapToInt(ChemVOs.Store::overstock).sum(), (int) items.stream().filter(i -> !msds.contains(i.getId())).count());
    }

    /** 定时任务 / 大屏：超储的库存（定时任务里没有登录用户，不算权限） */
    public List<ChemVOs.StockRow> overstocked() {
        return build(storeMapper.selectList(Wrappers.<ChemStore>lambdaQuery()), false).stream().filter(s -> Integer.valueOf(1).equals(s.store().getStatus()))
                .flatMap(s -> s.stocks().stream().filter(ChemVOs.StockRow::overstock)).toList();
    }

    // ============================== 内部方法 ==============================

    private List<ChemVOs.Store> build(List<ChemStore> stores, boolean withAccess) {
        if (stores.isEmpty()) {
            return List.of();
        }
        List<ChemStock> stocks = stockMapper.selectList(Wrappers.<ChemStock>lambdaQuery()
                .in(ChemStock::getStoreId, stores.stream().map(ChemStore::getId).toList()));
        Map<Long, ChemItem> items = stocks.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(stocks.stream().map(ChemStock::getChemId).distinct().toList())
                .stream().collect(Collectors.toMap(ChemItem::getId, Function.identity()));
        Set<Long> msds = msdsOf(items.keySet());
        Map<Long, List<ChemStock>> byStore = stocks.stream().collect(Collectors.groupingBy(ChemStock::getStoreId));
        return stores.stream().map(st -> {
            List<ChemStock> l = byStore.getOrDefault(st.getId(), List.of()).stream()
                    .filter(x -> items.containsKey(x.getChemId())).toList();
            List<ChemVOs.StockRow> rows = l.stream().map(x -> {
                ChemItem i = items.get(x.getChemId());
                return new ChemVOs.StockRow(x.getId(), i.getId(), i.getName(), i.getCas(), i.getCategory(), x.getQty(), x.getMaxQty(),
                        i.getCriticalQty(), ChemRules.overstock(x.getQty(), x.getMaxQty()), msds.contains(i.getId()));
            }).sorted((a, b) -> a.chemName().compareTo(b.chemName())).toList();
            int people = st.getPeople() == null ? 0 : st.getPeople();
            ChemRules.Result design = ChemRules.identify(l.stream().map(x -> part(items.get(x.getChemId()), x.getMaxQty())).toList(), people);
            ChemRules.Result current = ChemRules.identify(l.stream().map(x -> part(items.get(x.getChemId()), x.getQty())).toList(), people);
            return new ChemVOs.Store(st, rows, design, current, (int) rows.stream().filter(ChemVOs.StockRow::overstock).count(), withAccess && canFlow(st));
        }).toList();
    }

    private static ChemRules.Part part(ChemItem i, BigDecimal q) {
        return new ChemRules.Part(i.getName(), q, i.getCriticalQty(), i.getBeta());
    }

    private boolean canFlow(ChemStore s) {
        return StpUtil.hasPermission(PERM_EDIT) || Objects.equals(s.getManagerId(), StpUtil.getLoginIdAsLong());
    }

    private Set<Long> msdsOf(Collection<Long> chemIds) {
        if (chemIds.isEmpty()) {
            return Set.of();
        }
        return fileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                        .eq(SysFile::getBizType, BIZ_TYPE).eq(SysFile::getTag, TAG_MSDS).in(SysFile::getBizId, chemIds)
                        .select(SysFile::getBizId))
                .stream().map(SysFile::getBizId).collect(Collectors.toSet());
    }

    private ChemStore loadStore(Long id) {
        ChemStore s = storeMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "存储单元不存在");
        }
        return s;
    }

    private ChemStock loadStock(Long id) {
        ChemStock s = stockMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "库存记录不存在");
        }
        return s;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
