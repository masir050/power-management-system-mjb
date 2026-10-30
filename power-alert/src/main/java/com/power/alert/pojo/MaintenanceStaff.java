package com.power.alert.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 运维人员实体，映射 maintenance_staff 表
 * <p>管理电力运维团队的人员信息，包括所属部门、职位、
 * 联系方式和工作状态（在岗/休假/离职）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaintenanceStaff extends BaseEntity {
    /** 人员姓名 */
    private String name;
    /** 所属部门 */
    private String department;
    /** 职位 */
    private String position;
    /** 联系电话 */
    private String phone;
    /** 工作状态：在岗、休假、离职 */
    private String workStatus;
}
