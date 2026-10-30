package com.power.alert.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.alert.pojo.MaintenanceRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 检修记录 Mapper 接口
 * <p>自定义方法对应 MaintenanceRecordMapper.xml 中的手写 SQL，
 * 包含三表关联查询、故障统计和近期告警。</p>
 */
public interface MaintenanceRecordMapper extends BaseMapper<MaintenanceRecord> {

    /** 查询今日告警数量 */
    long countTodayAlerts(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /** 待处理告警数（PENDING 状态），运维首页最关心的一个数字 */
    long countPendingAlerts();

    /** 查询检修记录详情（三表 JOIN：record -> equipment -> staff） */
    Map<String, Object> selectRecordWithDetails(@Param("id") Long id);

    /** 分页查询检修记录（支持按故障类型、优先级、时间范围动态过滤） */
    List<Map<String, Object>> selectRecordPage(@Param("faultType") String faultType,
                                                @Param("repairPriority") String repairPriority,
                                                @Param("equipmentId") Long equipmentId,
                                                @Param("staffId") Long staffId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<MaintenanceRecord> selectByPageWithConditions(@Param("faultType") String faultType,
                                                       @Param("repairPriority") String repairPriority,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit,
                                                       @Param("sortField") String sortField,
                                                       @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("faultType") String faultType,
                                    @Param("repairPriority") String repairPriority);

    /** 统计检修记录概况（总数、各优先级数量、平均工时） */
    Map<String, Object> selectRecordStatistics();

    /** 按故障类型分组统计次数和平均修复工时 */
    List<Map<String, Object>> selectRecordGroupByFaultType();

    /** 查询最近的告警记录（LIMIT 限制条数） */
    List<Map<String, Object>> selectRecentAlerts(@Param("limit") int limit);

    /** 动态更新检修记录 */
    int updateRecordSelective(MaintenanceRecord record);
}
