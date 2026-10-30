package com.power.billing.service;

import com.power.billing.mapper.ElectricityBillMapper;
import com.power.billing.mapper.PaymentRecordMapper;
import com.power.billing.pojo.ElectricityBill;
import com.power.billing.pojo.PayRequest;
import com.power.billing.pojo.PaymentRecord;
import com.power.common.exception.BusinessException;
import com.power.common.security.CurrentUserContext;
import com.power.common.security.DataScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缴费业务
 *
 * <p>这是本项目里唯一一条「客户主动写库」的路径，所以权限、并发、状态流转三件事
 * 都在这里收口，不散落到 Controller：</p>
 *
 * <ul>
 *   <li><b>谁能缴</b>：客户只能缴自己名下的账单；管理员可柜台代收；运维没有收费职责。</li>
 *   <li><b>能缴多少</b>：只能缴欠费额以内的正数，缴多了直接拒绝而不是把余额记成负数。</li>
 *   <li><b>缴完什么状态</b>：欠费清零 → 已缴；还有欠款且已过期限 → 逾期；否则 → 部分缴纳。</li>
 *   <li><b>并发</b>：扣款 SQL 的 WHERE 里带余额与状态守卫，更新 0 行即代表被抢先，
 *       回滚并提示刷新，不引入悲观锁。</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 支持的支付方式。限定白名单，避免前端传任意字符串进库 */
    private static final List<String> METHODS = List.of("微信", "支付宝", "银行卡", "现金");

    private final ElectricityBillMapper billMapper;
    private final PaymentRecordMapper paymentMapper;

    public PaymentService(ElectricityBillMapper billMapper, PaymentRecordMapper paymentMapper) {
        this.billMapper = billMapper;
        this.paymentMapper = paymentMapper;
    }

    /**
     * 缴费（客户自助或管理员柜台代收）
     *
     * @param billId 账单 ID
     * @param req    金额与支付方式；金额为空表示结清剩余全部欠费
     * @return 缴费结果快照，供前端直接展示「流水号 + 结清后状态」
     */
    @Transactional
    public Map<String, Object> pay(Long billId, PayRequest req) {
        CurrentUserContext.CurrentUser user = CurrentUserContext.get();
        if (user == null) {
            throw new DataScope.ForbiddenException("未登录，无法缴费");
        }

        ElectricityBill bill = billMapper.selectForPay(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在或已删除");
        }

        // 客户只能缴自己名下的账单；非客户只有管理员能代收
        DataScope.assertCustomerOwns(bill.getCustomerId());
        if (!user.isCustomer() && !user.isAdmin()) {
            throw new DataScope.ForbiddenException("运维人员无收费权限，请联系总负责人");
        }

        if ("已缴".equals(bill.getPaymentStatus())) {
            throw new BusinessException("该账单已结清，无需重复缴费");
        }

        BigDecimal arrears = nvl(bill.getArrearsAmount());
        if (arrears.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该账单当前没有欠费");
        }

        // 金额：缺省视为一次结清；不接受 0、负数或超出欠费额
        BigDecimal amount = req == null || req.getAmount() == null ? arrears : req.getAmount();
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("缴费金额必须大于 0");
        }
        if (amount.compareTo(arrears) > 0) {
            throw new BusinessException("缴费金额不能超过欠费金额 ¥" + arrears.toPlainString());
        }

        String method = req == null || req.getPaymentMethod() == null || req.getPaymentMethod().isBlank()
                ? "微信" : req.getPaymentMethod().trim();
        if (!METHODS.contains(method)) {
            throw new BusinessException("不支持的支付方式：" + method);
        }

        BigDecimal remain = arrears.subtract(amount);
        String nextStatus;
        if (remain.compareTo(BigDecimal.ZERO) == 0) {
            nextStatus = "已缴";
        } else if (bill.getPaymentDeadline() != null && bill.getPaymentDeadline().isBefore(LocalDate.now())) {
            nextStatus = "逾期";
        } else {
            nextStatus = "部分缴纳";
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = billMapper.applyPayment(billId, amount, nextStatus, method, now);
        if (rows == 0) {
            // 走到这里只有两种可能：账单刚被别人缴清，或余额已被扣到不够本次金额
            throw new BusinessException("该账单状态已变化，请刷新后重试");
        }

        PaymentRecord record = new PaymentRecord();
        record.setBillId(billId);
        record.setCustomerId(bill.getCustomerId());
        record.setAmount(amount);
        record.setPaymentMethod(method);
        record.setPaymentNo(nextPaymentNo());
        record.setPayerType(user.isCustomer() ? "SELF" : "AGENT");
        record.setOperatorId(user.isCustomer() ? null : user.userId());
        record.setPayTime(now);
        paymentMapper.insertPayment(record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billId", billId);
        result.put("paymentNo", record.getPaymentNo());
        result.put("amount", amount);
        result.put("paymentMethod", method);
        result.put("payTime", now);
        result.put("paymentStatus", nextStatus);
        result.put("paidAmount", nvl(bill.getPaidAmount()).add(amount));
        result.put("arrearsAmount", remain);
        return result;
    }

    /** 某张账单的缴费记录（客户只能看自己的账单） */
    public List<PaymentRecord> listByBill(Long billId) {
        ElectricityBill bill = billMapper.selectForPay(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在或已删除");
        }
        DataScope.assertCustomerOwns(bill.getCustomerId());
        return paymentMapper.selectByBillId(billId);
    }

    /** 当前客户最近的缴费记录；管理员/运维调用返回空列表 */
    public List<PaymentRecord> recentForCurrentCustomer(int limit) {
        Long customerId = DataScope.customerScope();
        if (customerId == null) {
            return List.of();
        }
        return paymentMapper.selectRecentByCustomer(customerId, Math.max(1, Math.min(limit, 50)));
    }

    /** 流水号：时间 + 4 位随机，唯一索引兜底，重复即回滚重试由用户触发 */
    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
