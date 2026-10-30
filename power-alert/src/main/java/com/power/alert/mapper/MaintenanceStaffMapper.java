package com.power.alert.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.alert.pojo.MaintenanceStaff;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 运维人员 Mapper 接口
 * <p>自定义方法对应 MaintenanceStaffMapper.xml 中的手写 SQL，
 * 包含人员与检修记录的关联统计和部门分组。</p>
 */
public interface MaintenanceStaffMapper extends BaseMapper<MaintenanceStaff> {

    /** 分页查询运维人员（支持按姓名、部门、职位、状态动态过滤） */
    List<Map<String, Object>> selectStaffPage(@Param("name") String name,
                                               @Param("department") String department,
                                               @Param("position") String position,
                                               @Param("workStatus") String workStatus);

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<MaintenanceStaff> selectByPageWithConditions(@Param("name") String name,
                                                      @Param("department") String department,
                                                      @Param("position") String position,
                                                      @Param("workStatus") String workStatus,
                                                      @Param("offset") int offset,
                                                      @Param("limit") int limit,
                                                      @Param("sortField") String sortField,
                                                      @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("name") String name,
                                    @Param("department") String department,
                                    @Param("position") String position,
                                    @Param("workStatus") String workStatus);

    /** 查询运维人员及其参与检修记录数（LEFT JOIN + GROUP BY） */
    List<Map<String, Object>> selectStaffWithRecordCount(@Param("department") String department,
                                                          @Param("workStatus") String workStatus);

    /** 统计人员概况（总数、在岗/休假/离职数量、部门数） */
    Map<String, Object> selectStaffStatistics();

    /** 按部门分组统计人员数量 */
    List<Map<String, Object>> selectStaffGroupByDepartment();

    /** 动态更新人员信息 */
    int updateStaffSelective(MaintenanceStaff staff);
}
