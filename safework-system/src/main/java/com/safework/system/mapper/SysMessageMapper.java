package com.safework.system.mapper;

import com.safework.system.message.SysMessage;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {

    /** 清理任务：删除全部企业很早以前的已读消息（定时任务没有租户上下文，跳过租户插件） */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_message WHERE read_flag = 1 AND create_time < #{before}")
    int deleteReadBefore(@Param("before") LocalDateTime before);
}
