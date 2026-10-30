package com.power.billing.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 用电客户实体，映射 electricity_customer 表
 * <p>管理所有用电客户信息，按居民、商业、工业、农业分类，
 * 跟踪客户的用电状态（正常/暂停/销户）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ElectricityCustomer extends BaseEntity {
    /** 客户名称 */
    private String customerName;
    /** 用电类别：居民、商业、工业、农业 */
    private String electricityCategory;
    /** 地址 */
    private String address;
    /** 联系方式 */
    private String contact;
    /** 用电状态：正常用电、暂停、销户 */
    private String electricityStatus;
}
