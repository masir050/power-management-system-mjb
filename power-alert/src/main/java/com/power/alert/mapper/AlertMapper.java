package com.power.alert.mapper;

import com.power.alert.pojo.AlertRecord;
import com.power.common.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警 Mapper
 * <p>除了继承的基础 CRUD，新增的查询都围绕"告警列表页"和"仪表盘"两个高频场景设计，
 * 复杂 SQL 全部写在 AlertMapper.xml 里。</p>
 */
public interface AlertMapper extends BaseMapper<AlertRecord> {

    /** 告警列表（多条件 + 分页 + 白名单排序） */
    List<AlertRecord> selectAlertPage(@Param("alertLevel") String alertLevel,
                                      @Param("alertStatus") String alertStatus,
                                      @Param("faultType") String faultType,
                                      @Param("equipmentId") Long equipmentId,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit,
                                      @Param("sortField") String sortField,
                                      @Param("sortOrder") String sortOrder);

    /** 告警列表计数（条件必须与 selectAlertPage 完全一致，否则分页总数会对不上） */
    long countAlertPage(@Param("alertLevel") String alertLevel,
                        @Param("alertStatus") String alertStatus,
                        @Param("faultType") String faultType,
                        @Param("equipmentId") Long equipmentId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

    /** 告警统计：总数、各状态数量、各等级数量、平均处理时长（CASE WHEN 条件聚合 + TIMESTAMPDIFF） */
    Map<String, Object> selectAlertStatistics();

    /** 按故障类型分组统计 */
    List<Map<String, Object>> selectAlertGroupByFaultType();

    /** 按小时统计最近 24 小时告警趋势（用于前端折线图） */
    List<Map<String, Object>> selectHourlyTrend(@Param("startTime") LocalDateTime startTime);

    /** 近期告警（设备表 LEFT JOIN，设备被删也要能显示告警） */
    List<Map<String, Object>> selectRecentAlerts(@Param("limit") int limit);

    /** 状态流转：带前置状态校验，避免"已处理"告警被并发改成"待处理" */
    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("handlerId") Long handlerId,
                     @Param("handleResult") String handleResult,
                     @Param("occurTime") LocalDateTime occurTime);
}
