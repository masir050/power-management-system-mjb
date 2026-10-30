package com.power.billing.pojo;

import lombok.Data;

import java.math.BigDecimal;

/** 缴费请求体：只允许客户端决定「缴多少」和「怎么缴」 */
@Data
public class PayRequest {
    /** 本次缴费金额，为空表示结清剩余全部欠费 */
    private BigDecimal amount;
    /** 支付方式：微信 / 支付宝 / 银行卡 / 现金 */
    private String paymentMethod;
}
