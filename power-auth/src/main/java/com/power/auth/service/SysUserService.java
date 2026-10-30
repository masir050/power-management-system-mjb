package com.power.auth.service;

import com.power.auth.mapper.SysUserMapper;
import com.power.auth.pojo.SysUser;
import com.power.common.mapper.BaseMapper;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 系统用户业务实现
 *
 * <p>用户表的可排序列只有审计列，默认实现即可满足；
 * 这里显式覆写 {@link #isSortableColumn} 是因为 sys_user 允许按 username 排序
 * （后台用户列表按账号字母序更常见），而其它表未必有这一列。</p>
 */
@Service
public class SysUserService extends BaseServiceImpl<SysUser> {

    private static final Set<String> SORTABLE = Set.of("id", "username", "create_time", "update_time");

    private final SysUserMapper sysUserMapper;

    public SysUserService(SysUserMapper mapper) {
        super(mapper);
        this.sysUserMapper = mapper;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<SysUser> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        return super.pageList(pageDTO, conditions);
    }
}
