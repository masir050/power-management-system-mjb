package com.power.device.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDate;

/**
 * 变电站实体，映射 substation 表
 * <p>记录变电站的基础信息：电压等级、投运日期、运行状态等。
 * 通过 area_id 关联到所属供电区域。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Substation extends BaseEntity {
    /** 变电站名称 */
    private String name;
    /** 电压等级（如 110kV、220kV、500kV） */
    private String voltageLevel;
    /** 投运日期 */
    private LocalDate commissionDate;
    /** 运行状态：运行中、停运、检修中 */
    private String runStatus;
    /** 管辖年限 */
    private Integer jurisdictionYears;
    /** 管理等级：A级、B级等 */
    private String managementLevel;
    /** 所属供电区域 ID，关联 power_area 表 */
    private Long areaId;
}
