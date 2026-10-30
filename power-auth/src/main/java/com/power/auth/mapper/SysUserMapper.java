package com.power.auth.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.auth.pojo.SysUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 系统用户 Mapper 接口
 * <p>自定义方法对应 SysUserMapper.xml 中的手写 SQL，
 * 包含登录查询和用户统计。</p>
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 根据用户名查询用户（登录认证用，LIMIT 1） */
    SysUser selectUserByUsername(@Param("username") String username);

    /** 分页查询用户（支持按用户名、昵称、状态动态过滤） */
    List<Map<String, Object>> selectUserPage(@Param("username") String username,
                                              @Param("nickname") String nickname,
                                              @Param("status") Integer status,
                                              @Param("email") String email);

    /** 统计用户概况（总数、启用/禁用数量） */
    Map<String, Object> selectUserStatistics();

    /** 动态更新用户信息 */
    int updateUserSelective(SysUser user);
}
