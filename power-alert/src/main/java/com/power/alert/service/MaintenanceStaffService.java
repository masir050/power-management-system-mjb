package com.power.alert.service;

import com.power.alert.mapper.MaintenanceStaffMapper;
import com.power.alert.pojo.MaintenanceStaff;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运维人员业务实现类
 * <p>提供人员自定义查询方法，底层由 MaintenanceStaffMapper.xml 中的手写 SQL 驱动，
 * 包含人员与检修记录的关联统计和部门分组。</p>
 */
@Service
public class MaintenanceStaffService extends BaseServiceImpl<MaintenanceStaff>
        implements BaseService<MaintenanceStaff> {

    /** 允许排序的列白名单（理由同 MaintenanceRecordService） */
    private static final Set<String> SORTABLE = Set.of(
            "id", "name", "department", "position", "work_status", "create_time", "update_time");

    private final MaintenanceStaffMapper staffMapper;

    public MaintenanceStaffService(MaintenanceStaffMapper mapper) {
        super(mapper);
        this.staffMapper = mapper;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<MaintenanceStaff> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        String name = conditions.get("name");
        String department = conditions.get("department");
        String position = conditions.get("position");
        String workStatus = conditions.get("workStatus");
        boolean hasConditions = (name != null && !name.isEmpty())
                || (department != null && !department.isEmpty())
                || (position != null && !position.isEmpty())
                || (workStatus != null && !workStatus.isEmpty());
        if (!hasConditions) {
            return super.pageList(pageDTO, conditions);
        }

        PageQuery query = resolvePageQuery(pageDTO);
        List<MaintenanceStaff> records = staffMapper.selectByPageWithConditions(
                name, department, position, workStatus, query.offset(), query.size(),
                query.sortField(), query.sortOrder());
        long total = staffMapper.countByPageWithConditions(name, department, position, workStatus);

        PageResult<MaintenanceStaff> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /** 查询运维人员列表（多条件过滤，条数由 Mapper 内部限制） */
    public List<Map<String, Object>> queryStaffPage(String name, String department,
                                                    String position, String workStatus) {
        return staffMapper.selectStaffPage(name, department, position, workStatus);
    }

    /** 查询人员及参与检修记录数（LEFT JOIN + GROUP BY） */
    public List<Map<String, Object>> queryStaffWithRecordCount(String department, String workStatus) {
        return staffMapper.selectStaffWithRecordCount(department, workStatus);
    }

    /** 统计人员概况 */
    public Map<String, Object> queryStaffStatistics() {
        return staffMapper.selectStaffStatistics();
    }

    /** 按部门分组统计 */
    public List<Map<String, Object>> queryStaffGroupByDepartment() {
        return staffMapper.selectStaffGroupByDepartment();
    }

    /** 动态更新人员 */
    public int updateStaffSelective(MaintenanceStaff staff) {
        return staffMapper.updateStaffSelective(staff);
    }
}
