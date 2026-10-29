package com.safework.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.system.dto.DeptSaveDTO;
import com.safework.system.dto.DeptVO;
import com.safework.system.entity.SysDept;
import com.safework.system.entity.SysUser;
import com.safework.system.mapper.SysDeptMapper;
import com.safework.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门：树形结构，用 parent_id 表示上下级。
 * 一个企业的部门数量通常只有几十个，直接全部查出来在内存里组装树，比递归 SQL 简单可靠。
 */
@Service
@RequiredArgsConstructor
public class DeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public List<SysDept> listAll() {
        return deptMapper.selectList(Wrappers.<SysDept>lambdaQuery().orderByAsc(SysDept::getSort));
    }

    /** 部门 ID → 名称，给列表页填充部门名称用 */
    public Map<Long, String> nameMap() {
        return listAll().stream().collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
    }

    public String nameOf(Long deptId) {
        return deptId == null ? null : nameMap().get(deptId);
    }

    public List<DeptVO> tree() {
        Map<Long, DeptVO> map = new LinkedHashMap<>();
        for (SysDept d : listAll()) {
            DeptVO vo = new DeptVO();
            vo.setId(d.getId());
            vo.setParentId(d.getParentId());
            vo.setDeptName(d.getDeptName());
            vo.setLeader(d.getLeader());
            vo.setPhone(d.getPhone());
            vo.setSort(d.getSort());
            vo.setStatus(d.getStatus());
            vo.setCreateTime(d.getCreateTime());
            map.put(vo.getId(), vo);
        }
        List<DeptVO> roots = new ArrayList<>();
        for (DeptVO vo : map.values()) {
            DeptVO parent = map.get(vo.getParentId());
            if (parent != null) {
                parent.getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    /** 自己和所有下级部门的 ID（按部门筛选用户时，选“生产部”要包含下面的车间） */
    public Set<Long> selfAndDescendants(Long deptId) {
        List<SysDept> all = listAll();
        Map<Long, List<Long>> children = all.stream().collect(Collectors.groupingBy(SysDept::getParentId,
                Collectors.mapping(SysDept::getId, Collectors.toList())));
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(deptId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (result.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return result;
    }

    public SysDept getById(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(404, "部门不存在");
        }
        return dept;
    }

    public Long create(DeptSaveDTO dto) {
        SysDept dept = new SysDept();
        fill(dept, dto);
        deptMapper.insert(dept);
        return dept.getId();
    }

    public void update(Long id, DeptSaveDTO dto) {
        SysDept dept = getById(id);
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        // 不能把部门移动到自己或自己的下级下面，否则会形成环
        if (selfAndDescendants(id).contains(parentId)) {
            throw new BizException("上级部门不能是自己或自己的下级部门");
        }
        fill(dept, dto);
        deptMapper.updateById(dept);
    }

    public void remove(Long id) {
        getById(id);
        if (deptMapper.selectCount(Wrappers.<SysDept>lambdaQuery().eq(SysDept::getParentId, id)) > 0) {
            throw new BizException("请先删除下级部门");
        }
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getDeptId, id)) > 0) {
            throw new BizException("部门下还有用户，不能删除");
        }
        deptMapper.deleteById(id);
    }

    private void fill(SysDept dept, DeptSaveDTO dto) {
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId != 0L) {
            getById(parentId);
        }
        dept.setParentId(parentId);
        dept.setDeptName(dto.getDeptName());
        dept.setLeader(dto.getLeader());
        dept.setPhone(dto.getPhone());
        dept.setSort(dto.getSort() == null ? 0 : dto.getSort());
        dept.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }
}
