package com.power.billing.mapper;

import com.power.billing.pojo.PaymentRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 缴费流水 Mapper */
public interface PaymentRecordMapper {

    /** 写一条缴费流水 */
    int insertPayment(PaymentRecord record);

    /** 查某张账单的全部缴费记录，按时间倒序 */
    List<PaymentRecord> selectByBillId(@Param("billId") Long billId);

    /** 查某个客户最近的缴费记录（客户首页「缴费记录」用） */
    List<PaymentRecord> selectRecentByCustomer(@Param("customerId") Long customerId,
                                               @Param("limit") int limit);
}
