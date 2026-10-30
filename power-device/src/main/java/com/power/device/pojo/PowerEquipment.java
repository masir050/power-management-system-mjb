package com.power.device.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDate;

/**
 * 电力设备实体，映射 power_equipment 表
 * <p>记录变电站内的具体设备信息：变压器、断路器、隔离开关等。
 * 通过 substation_id 关联到所属变电站。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PowerEquipment extends BaseEntity {
    /** 设备名称 */
    private String equipmentName;
    /** 设备类型：变压器、断路器、隔离开关 */
    private String equipmentType;
    /** 额定参数描述 */
    private String ratedParams;
    /** 安装位置 */
    private String installLocation;
    /** 出厂日期 */
    private LocalDate manufactureDate;
    /** 影响范围描述 */
    private String impactScope;
    /** 危害等级：一级、二级、三级 */
    private String hazardLevel;
    /** 安装批次编号 */
    private String installBatch;
    /** 设备数量 */
    private Integer quantity;
    /** 投运批次编号 */
    private String commissionBatch;
    /** 所属变电站 ID，关联 substation 表 */
    private Long substationId;
}
