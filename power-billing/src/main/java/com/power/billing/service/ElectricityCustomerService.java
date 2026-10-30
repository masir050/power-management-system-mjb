package com.power.billing.service;

import com.power.billing.mapper.ElectricityCustomerMapper;
import com.power.billing.pojo.ElectricityCustomer;
import com.power.billing.pojo.ElectricityCustomerVO;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用电客户业务实现类
 * <p>提供客户自定义查询方法，底层由 ElectricityCustomerMapper.xml 中的手写 SQL 驱动。</p>
 */
@Service
public class ElectricityCustomerService extends BaseServiceImpl<ElectricityCustomer> implements BaseService<ElectricityCustomer> {

    /** 允许排序的列（白名单，防止 ORDER BY 注入与未知列报错） */
    private static final Set<String> SORTABLE = Set.of("id", "customer_name", "electricity_category", "create_time", "update_time");

    private final ElectricityCustomerMapper customerMapper;

    public ElectricityCustomerService(ElectricityCustomerMapper mapper) {
        super(mapper);
        this.customerMapper = mapper;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<ElectricityCustomer> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        String customerName = conditions.get("customerName");
        String electricityCategory = conditions.get("electricityCategory");
        String electricityStatus = conditions.get("electricityStatus");
        boolean hasConditions = (customerName != null && !customerName.isEmpty())
                || (electricityCategory != null && !electricityCategory.isEmpty())
                || (electricityStatus != null && !electricityStatus.isEmpty());
        if (!hasConditions) {
            return super.pageList(pageDTO, conditions);
        }
        PageQuery query = resolvePageQuery(pageDTO);
        List<ElectricityCustomer> records = customerMapper.selectByPageWithConditions(
                customerName, electricityCategory, electricityStatus, query.offset(), query.size(), query.sortField(), query.sortOrder());
        long total = customerMapper.countByPageWithConditions(customerName, electricityCategory, electricityStatus);
        PageResult<ElectricityCustomer> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /** 查询客户及名下电表（一对多 LEFT JOIN） */
    public List<Map<String, Object>> queryCustomerWithMeters(Long id) {
        return customerMapper.selectCustomerWithMeters(id);
    }

    /** 分页查询客户（多条件动态过滤） */
    public List<ElectricityCustomerVO> queryCustomerPage(String customerName, String electricityCategory,
                                                          String electricityStatus, String contact) {
        return customerMapper.selectCustomerPage(customerName, electricityCategory, electricityStatus, contact);
    }

    /** 统计客户整体概况 */
    public Map<String, Object> queryCustomerStatistics() {
        return customerMapper.selectCustomerStatistics();
    }

    /** 按用电类别分组统计 */
    public List<Map<String, Object>> queryCustomerGroupByCategory() {
        return customerMapper.selectCustomerGroupByCategory();
    }

    /** 动态更新客户 */
    public int updateCustomerSelective(ElectricityCustomer customer) {
        return customerMapper.updateCustomerSelective(customer);
    }
}
