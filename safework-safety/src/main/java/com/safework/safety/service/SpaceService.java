package com.safework.safety.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.SpaceOptionVO;
import com.safework.safety.dto.SpaceQueryDTO;
import com.safework.safety.dto.SpaceSaveDTO;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.mapper.ConfinedSpaceMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.service.DeptService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final ConfinedSpaceMapper spaceMapper;
    private final DeptService deptService;
    private final UserService userService;

    public Page<ConfinedSpace> page(SpaceQueryDTO q) {
        String kw = q.getKeyword();
        Set<Long> deptIds = q.getDeptId() == null ? null : deptService.selfAndDescendants(q.getDeptId());
        Page<ConfinedSpace> page = spaceMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()),
                Wrappers.<ConfinedSpace>lambdaQuery()
                        .and(StringUtils.hasText(kw), w -> w
                                .like(ConfinedSpace::getSpaceCode, kw).or()
                                .like(ConfinedSpace::getSpaceName, kw).or()
                                .like(ConfinedSpace::getLocation, kw))
                        .eq(StringUtils.hasText(q.getSpaceType()), ConfinedSpace::getSpaceType, q.getSpaceType())
                        .eq(StringUtils.hasText(q.getRiskLevel()), ConfinedSpace::getRiskLevel, q.getRiskLevel())
                        .eq(q.getStatus() != null, ConfinedSpace::getStatus, q.getStatus())
                        .in(deptIds != null, ConfinedSpace::getDeptId, deptIds)
                        .orderByAsc(ConfinedSpace::getSpaceCode));
        Map<Long, String> deptNames = deptService.nameMap();
        page.getRecords().forEach(s -> s.setDeptName(deptNames.get(s.getDeptId())));
        return page;
    }

    public ConfinedSpace getById(Long id) {
        ConfinedSpace s = spaceMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "受限空间不存在");
        }
        s.setDeptName(deptService.nameOf(s.getDeptId()));
        return s;
    }

    /** 作业票、隐患关联时使用：必须存在且启用 */
    public ConfinedSpace getEnabled(Long id) {
        ConfinedSpace s = getById(id);
        if (s.getStatus() == null || s.getStatus() != 1) {
            throw new BizException("受限空间【" + s.getSpaceName() + "】已停用");
        }
        return s;
    }

    /** 扫码：跳过租户隔离，用不可猜测的令牌定位 */
    public ConfinedSpace getByQrToken(String token) {
        ConfinedSpace s = token == null || token.length() != 32 ? null : spaceMapper.selectByQrToken(token);
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BizException(404, "二维码无效或该空间已停用");
        }
        return s;
    }

    public List<SpaceOptionVO> options() {
        return spaceMapper.selectList(Wrappers.<ConfinedSpace>lambdaQuery()
                        .eq(ConfinedSpace::getStatus, 1)
                        .orderByAsc(ConfinedSpace::getSpaceCode))
                .stream()
                .map(s -> new SpaceOptionVO(s.getId(), s.getSpaceCode(), s.getSpaceName(), s.getLocation(), s.getRiskLevel()))
                .toList();
    }

    public Long create(SpaceSaveDTO dto) {
        checkCodeUnique(dto.getSpaceCode(), null);
        ConfinedSpace s = new ConfinedSpace();
        fill(s, dto);
        s.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        s.setStatus(1);
        spaceMapper.insert(s);
        return s.getId();
    }

    public void update(Long id, SpaceSaveDTO dto) {
        ConfinedSpace s = getById(id);
        checkCodeUnique(dto.getSpaceCode(), id);
        fill(s, dto);
        spaceMapper.updateById(s);
    }

    public void changeStatus(Long id, Integer status) {
        ConfinedSpace s = getById(id);
        s.setStatus(status);
        spaceMapper.updateById(s);
    }

    /** 重新生成二维码：旧的二维码立即失效（比如二维码贴纸被拍照外传） */
    public void resetQrToken(Long id) {
        ConfinedSpace s = getById(id);
        s.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        spaceMapper.updateById(s);
    }

    private void checkCodeUnique(String code, Long excludeId) {
        Long count = spaceMapper.selectCount(Wrappers.<ConfinedSpace>lambdaQuery()
                .eq(ConfinedSpace::getSpaceCode, code)
                .ne(excludeId != null, ConfinedSpace::getId, excludeId));
        if (count > 0) {
            throw new BizException("空间编号【" + code + "】已存在");
        }
    }

    private void fill(ConfinedSpace s, SpaceSaveDTO dto) {
        if (dto.getDeptId() != null) {
            deptService.getById(dto.getDeptId());
        }
        s.setSpaceCode(dto.getSpaceCode());
        s.setSpaceName(dto.getSpaceName());
        s.setSpaceType(dto.getSpaceType());
        s.setLocation(dto.getLocation());
        s.setDeptId(dto.getDeptId());
        s.setMedium(dto.getMedium());
        s.setRiskLevel(dto.getRiskLevel());
        s.setRemark(dto.getRemark());
        if (dto.getResponsibleId() != null) {
            SysUser u = userService.getById(dto.getResponsibleId());
            s.setResponsibleId(u.getId());
            s.setResponsibleName(u.getNickname());
        } else {
            s.setResponsibleId(null);
            s.setResponsibleName(null);
        }
    }
}
