package com.power.billing.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缴费流水，映射 payment_record 表
 *
 * <p>一张账单允许分多次缴纳（部分缴纳是业务常态），所以流水必须独立成表：
 * 账单行上只保留「最近一次缴费时间 / 方式」，历史每一次实缴都落在这里，
 * 客户能查缴费记录，管理员能据此对账。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PaymentRecord extends BaseEntity {
    /** 账单 ID */
    private Long billId;
    /** 缴费客户 ID */
    private Long customerId;
    /** 本次缴费金额（元） */
    private BigDecimal amount;
    /** 支付方式：微信、支付宝、银行卡、现金 */
    private String paymentMethod;
    /** 交易流水号，前端展示与对账用 */
    private String paymentNo;
    /** SELF 客户自助 / AGENT 柜台代收 */
    private String payerType;
    /** 代收操作人 ID（自助缴费为空） */
    private Long operatorId;
    /** 缴费时刻 */
    private LocalDateTime payTime;
}
