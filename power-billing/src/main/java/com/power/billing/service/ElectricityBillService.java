package com.power.billing.service;

import com.power.billing.mapper.ElectricityBillMapper;
import com.power.billing.pojo.ElectricityBill;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import com.power.common.security.DataScope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 电费账单业务实现类
 * <p>提供账单自定义查询方法，底层由 ElectricityBillMapper.xml 中的手写 SQL 驱动，
 * 包含三表关联查询、欠费统计和年度聚合分析。</p>
 */
@Service
public class ElectricityBillService extends BaseServiceImpl<ElectricityBill> implements BaseService<ElectricityBill> {

    /** 允许排序的列（白名单，防止 ORDER BY 注入与未知列报错） */
    private static final Set<String> SORTABLE = Set.of("id", "billing_year", "total_consumption", "arrears_amount", "payment_status", "payment_deadline", "create_time", "update_time");

    private final ElectricityBillMapper billMapper;

    public ElectricityBillService(ElectricityBillMapper mapper) {
        super(mapper);
        this.billMapper = mapper;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<ElectricityBill> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        Integer billingYear = conditions.containsKey("billingYear") && !conditions.get("billingYear").isEmpty()
                ? Integer.valueOf(conditions.get("billingYear")) : null;
        String paymentStatus = conditions.get("paymentStatus");
        Long scope = DataScope.customerScope();
        boolean hasConditions = billingYear != null || (paymentStatus != null && !paymentStatus.isEmpty());
        if (!hasConditions && scope == null) {
            return super.pageList(pageDTO, conditions);
        }
        PageQuery query = resolvePageQuery(pageDTO);
        if (scope != null) {
            // 客户视图：分页必须把"属于我的账单"作为 SQL 条件，不能在内存里过滤——
            // 内存过滤会让 total 变成全库总数，分页条数也跟着错。
            PageResult<ElectricityBill> mine = new PageResult<>();
            long total = billMapper.countCustomerBillPage(scope, billingYear, paymentStatus);
            mine.setTotal(total);
            mine.setPages((long) Math.ceil((double) total / query.size()));
            mine.setCurrent(query.page());
            mine.setSize(query.size());
            mine.setRecords(billMapper.selectCustomerBillPage(scope, billingYear, paymentStatus,
                    query.offset(), query.size()));
            return mine;
        }
        List<ElectricityBill> records = billMapper.selectByPageWithConditions(
                billingYear, paymentStatus, query.offset(), query.size(), query.sortField(), query.sortOrder());
        long total = billMapper.countByPageWithConditions(billingYear, paymentStatus);
        PageResult<ElectricityBill> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /**
     * 查询账单详情（账单 + 客户；电表信息在 device 库，跨服务补齐）
     * <p>客户角色只能看自己的账单，越权访问抛 403。</p>
     */
    public Map<String, Object> queryBillWithDetails(Long id) {
        Long scope = DataScope.customerScope();
        Map<String, Object> data = billMapper.selectBillWithDetails(id, scope);
        if (data == null) {
            return null;
        }
        // 客户拿别人的账单 id 直接探测：查询条件已带 customer_id，查不到就是 null，
        // 但为了区分"不存在"和"不属于你"，这里再判一次归属，给出明确的 403
        if (scope != null) {
            Object owner = data.get("customerId");
            if (owner instanceof Number n) {
                DataScope.assertCustomerOwns(n.longValue());
            }
        }
        return data;
    }

    /**
     * 分页查询账单（支持年份、状态、用电量区间等多条件动态过滤）
     * <p>数据范围由 {@link DataScope} 决定，前端传不进来。</p>
     */
    public List<Map<String, Object>> queryBillPage(Integer billingYear, String paymentStatus,
                                                    Long meterId, String customerName,
                                                    Double minConsumption, Double maxConsumption) {
        return billMapper.selectBillPage(billingYear, paymentStatus, meterId, customerName,
                minConsumption, maxConsumption, DataScope.customerScope());
    }

    /** 查询未缴/逾期账单 */
    public List<Map<String, Object>> queryUnpaidBills(Integer billingYear, String customerName) {
        return billMapper.selectUnpaidBills(billingYear, customerName, DataScope.customerScope());
    }

    /** 按年度统计账单数据 */
    public List<Map<String, Object>> queryBillStatistics() {
        return billMapper.selectBillStatistics(DataScope.customerScope());
    }

    /** 欠费金额分布统计 */
    public List<Map<String, Object>> queryArrearsDistribution() {
        return billMapper.selectArrearsDistribution(DataScope.customerScope());
    }

    /** 动态更新账单 */
    public int updateBillSelective(ElectricityBill bill) {
        return billMapper.updateBillSelective(bill);
    }
}
