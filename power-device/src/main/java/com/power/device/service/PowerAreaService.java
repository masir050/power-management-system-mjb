package com.power.device.service;

import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import com.power.device.mapper.PowerAreaMapper;
import com.power.device.pojo.PowerArea;
import com.power.device.pojo.PowerAreaVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供电区域业务实现类
 * <p>提供区域自定义查询方法，底层由 PowerAreaMapper.xml 中的手写 SQL 驱动。</p>
 */
@Service
public class PowerAreaService extends BaseServiceImpl<PowerArea> implements BaseService<PowerArea> {

    /** 允许排序的列：区域列表按行政级别、名称排序较常见 */
    private static final Set<String> SORTABLE = Set.of(
            "id", "area_name", "admin_level", "create_time", "update_time");

    private final PowerAreaMapper powerAreaMapper;

    public PowerAreaService(PowerAreaMapper mapper) {
        super(mapper);
        this.powerAreaMapper = mapper;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<PowerArea> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        String areaName = conditions.get("areaName");
        String adminLevel = conditions.get("adminLevel");
        String leader = conditions.get("leader");
        boolean hasConditions = (areaName != null && !areaName.isEmpty())
                || (adminLevel != null && !adminLevel.isEmpty())
                || (leader != null && !leader.isEmpty());
        if (!hasConditions) {
            return super.pageList(pageDTO, conditions);
        }

        PageQuery query = resolvePageQuery(pageDTO);
        List<PowerArea> records = powerAreaMapper.selectByPageWithConditions(
                areaName, adminLevel, leader, query.offset(), query.size(),
                query.sortField(), query.sortOrder());
        long total = powerAreaMapper.countByPageWithConditions(areaName, adminLevel, leader);

        PageResult<PowerArea> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /** 分页查询供电区域（手写 SQL + 动态 WHERE） */
    public List<PowerAreaVO> queryAreaPage(String areaName, String adminLevel, String leader) {
        return powerAreaMapper.selectAreaPage(areaName, adminLevel, leader);
    }

    /** 查询各区域及其下辖变电站数量（LEFT JOIN + GROUP BY） */
    public List<Map<String, Object>> queryAreaWithSubstationCount(String areaName) {
        return powerAreaMapper.selectAreaWithSubstationCount(areaName);
    }

    /** 统计各行政级别区域分布（CASE WHEN 聚合） */
    public Map<String, Object> queryAreaStatistics() {
        return powerAreaMapper.selectAreaStatistics();
    }

    /** 动态更新区域（非空字段才更新） */
    public int updateAreaSelective(PowerArea area) {
        return powerAreaMapper.updateAreaSelective(area);
    }

    /** 批量逻辑删除区域（<foreach> IN 查询） */
    public int deleteAreaByIds(List<Long> ids) {
        return powerAreaMapper.deleteAreaByIds(ids);
    }
}
