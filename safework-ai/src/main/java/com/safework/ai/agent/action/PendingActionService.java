package com.safework.ai.agent.action;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 待确认操作的生成、确认、取消。
 * <ul>
 *   <li>存在 Redis，10 分钟过期：用户隔了很久再点确认，数据可能已经变了，让他重新问一次；</li>
 *   <li>绑定生成它的企业和用户：别人拿到 ID 也确认不了；</li>
 *   <li>确认时用 getAndDelete 取出：连点两次确认只会执行一次；</li>
 *   <li>执行前重新检查权限：生成卡片之后被收回了权限，确认也会失败。</li>
 * </ul>
 */
@Service
public class PendingActionService {

    static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY = "safework:agent:action:";
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final StringRedisTemplate redis;
    private final Map<String, AgentAction> actions;
    private final ObjectMapper json = new ObjectMapper();

    public PendingActionService(StringRedisTemplate redis, List<AgentAction> actions) {
        this.redis = redis;
        this.actions = actions.stream().collect(Collectors.toMap(AgentAction::name, Function.identity()));
    }

    public List<AgentAction> actions() {
        return actions.values().stream().sorted((a, b) -> a.name().compareTo(b.name())).toList();
    }

    /** 生成确认卡片（还没有执行任何修改） */
    public ActionCard propose(AgentAction action, com.fasterxml.jackson.databind.JsonNode args) {
        if (!StpUtil.hasPermission(action.permission())) {
            throw new IllegalArgumentException("当前用户没有权限执行【" + action.label() + "】");
        }
        AgentAction.Proposal p = action.propose(args);
        String id = UUID.randomUUID().toString().replace("-", "");
        PendingAction pending = new PendingAction(id, action.name(), TenantContext.get(), StpUtil.getLoginIdAsLong(),
                p.params(), action.label());
        try {
            redis.opsForValue().set(KEY + id, json.writeValueAsString(pending), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new ActionCard(id, action.name(), action.label(), p.fields(), p.tip(),
                LocalDateTime.now().plus(TTL).format(HM));
    }

    /** 用户点击【确认执行】 */
    public ActionResultVO confirm(String id) {
        load(id);
        // 取出的同时删除：并发的第二次确认拿到 null
        String raw = redis.opsForValue().getAndDelete(KEY + id);
        if (raw == null) {
            throw new BizException(410, "这个操作已经确认过或已过期");
        }
        PendingAction p = parse(raw);
        AgentAction action = actions.get(p.type());
        if (action == null) {
            throw new BizException(410, "操作类型已失效，请重新提问");
        }
        if (!StpUtil.hasPermission(action.permission())) {
            throw new BizException(403, "你已经没有【" + action.label() + "】的权限");
        }
        return new ActionResultVO(true, action.execute(p.params()));
    }

    /** 用户点击【取消】 */
    public void cancel(String id) {
        load(id);
        redis.delete(KEY + id);
    }

    /** 读取并检查归属：只有生成卡片的本人（同一企业）可以确认、取消，其他人一律当作不存在 */
    private PendingAction load(String id) {
        if (id == null || !id.matches("[0-9a-f]{32}")) {
            throw new BizException(404, "操作不存在");
        }
        String raw = redis.opsForValue().get(KEY + id);
        if (raw == null) {
            throw new BizException(410, "这个操作已经确认过或已过期（有效期 10 分钟），请重新让智能体生成");
        }
        PendingAction p = parse(raw);
        if (!Objects.equals(p.tenantId(), TenantContext.get()) || !Objects.equals(p.userId(), StpUtil.getLoginIdAsLong())) {
            throw new BizException(404, "操作不存在");
        }
        return p;
    }

    private PendingAction parse(String raw) {
        try {
            return json.readValue(raw, PendingAction.class);
        } catch (JsonProcessingException e) {
            throw new BizException(410, "操作数据已失效，请重新提问");
        }
    }

    /** params 里的数字从 JSON 读回来可能是 Integer 也可能是 Long */
    public static Long longOf(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : v instanceof Number n ? n.longValue() : Long.valueOf(v.toString());
    }
}
