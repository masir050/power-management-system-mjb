package com.power.device.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 供电区域实体，映射 power_area 表
 * <p>按省/市/县/乡镇分级管理供电区域，每个区域有负责人。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PowerArea extends BaseEntity {
    /** 区域名称（如"光明顶供电区"） */
    private String areaName;
    /** 行政级别：省级、市级、县级、乡镇级 */
    private String adminLevel;
    /** 管辖范围描述 */
    private String jurisdiction;
    /** 负责人姓名 */
    private String leader;
}
