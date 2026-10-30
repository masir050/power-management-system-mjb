package com.power.device.pojo;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 变电站视图对象
 * <p>包含变电站基本信息及关联的设备列表，用于关联查询展示。</p>
 */
@Data
public class SubstationVO {
    private Long id;
    private String name;
    /** 电压等级 */
    private String voltageLevel;
    /** 投运日期 */
    private LocalDate commissionDate;
    /** 运行状态 */
    private String runStatus;
    /** 管辖年限 */
    private Integer jurisdictionYears;
    /** 管理等级 */
    private String managementLevel;
    /** 所属区域 ID */
    private Long areaId;
    /** 该变电站下的设备列表 */
    private List<PowerEquipmentVO> equipmentList;
}
