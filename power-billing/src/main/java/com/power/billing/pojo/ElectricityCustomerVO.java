package com.power.billing.pojo;

import lombok.Data;

/**
 * 用电客户视图对象
 * <p>用于自定义 SQL 查询返回的客户信息，不含审计字段。</p>
 */
@Data
public class ElectricityCustomerVO {
    private Long id;
    private String customerName;
    private String electricityCategory;
    private String address;
    private String contact;
    private String electricityStatus;
}
