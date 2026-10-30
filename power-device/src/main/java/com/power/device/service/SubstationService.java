package com.power.device.service;

import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import com.power.device.mapper.SubstationMapper;
import com.power.device.pojo.Substation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 变电站业务实现类
 * <p>提供变电站自定义查询方法，底层由 SubstationMapper.xml 中的手写 SQL 驱动。</p>
 */
@Service
public class SubstationService extends BaseServiceImpl<Substation> implements BaseService<Substation> {

    /** 允许排序的列：变电站按电压等级、投运日期排序是运维常用视角 */
    private static final Set<String> SORTABLE = Set.of(
            "id", "name", "voltage_level", "commission_date", "create_time", "update_time");

    private final SubstationMapper substationMapper;

    public SubstationService(SubstationMapper mapper) {
        super(mapper);
        this.substationMapper = mapper;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<Substation> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        String name = conditions.get("name");
        String voltageLevel = conditions.get("voltageLevel");
        String runStatus = conditions.get("runStatus");
        boolean hasConditions = (name != null && !name.isEmpty())
                || (voltageLevel != null && !voltageLevel.isEmpty())
                || (runStatus != null && !runStatus.isEmpty());
        if (!hasConditions) {
            return super.pageList(pageDTO, conditions);
        }

        PageQuery query = resolvePageQuery(pageDTO);
        List<Substation> records = substationMapper.selectByPageWithConditions(
                name, voltageLevel, runStatus, query.offset(), query.size(),
                query.sortField(), query.sortOrder());
        long total = substationMapper.countByPageWithConditions(name, voltageLevel, runStatus);

        PageResult<Substation> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /** 查询变电站详情（含所属区域信息） */
    public Map<String, Object> querySubstationWithArea(Long id) {
        return substationMapper.selectSubstationWithArea(id);
    }

    /** 分页查询变电站（多条件动态过滤 + LEFT JOIN） */
    public List<Map<String, Object>> querySubstationPage(String name, String voltageLevel,
                                                          String runStatus, Long areaId,
                                                          String managementLevel) {
        return substationMapper.selectSubstationPage(name, voltageLevel, runStatus, areaId, managementLevel);
    }

    /** 统计变电站整体概况 */
    public Map<String, Object> querySubstationStatistics() {
        return substationMapper.selectSubstationStatistics();
    }

    /** 按电压等级分组统计 */
    public List<Map<String, Object>> querySubstationGroupByVoltage() {
        return substationMapper.selectSubstationGroupByVoltage();
    }

    /** 动态更新变电站 */
    public int updateSubstationSelective(Substation substation) {
        return substationMapper.updateSubstationSelective(substation);
    }
}
