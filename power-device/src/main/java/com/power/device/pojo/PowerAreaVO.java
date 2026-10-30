package com.power.device.pojo;

import lombok.Data;

/**
 * 供电区域视图对象
 * <p>用于自定义 SQL 查询返回的供电区域信息，不含基础审计字段。</p>
 */
@Data
public class PowerAreaVO {
    private Long id;
    /** 区域名称 */
    private String areaName;
    /** 行政级别 */
    private String adminLevel;
    /** 管辖范围 */
    private String jurisdiction;
    /** 负责人 */
    private String leader;
}
