package com.power.device.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * 运维汇总 Mapper
 *
 * <p>三组数字同库，一条 SQL 用标量子查询一次算完，避免调用方发三次远程请求。</p>
 */
@Mapper
public interface OperationsSummaryMapper {

    /** 设备总数 / 变电站总数与运行中数量 / 电表总数与正常数量 */
    Map<String, Object> selectOperationsSummary();
}
