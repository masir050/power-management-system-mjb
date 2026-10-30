package com.power.billing.service;

import com.power.billing.mapper.PriceStandardMapper;
import com.power.billing.pojo.PriceStandard;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 电价标准业务实现类
 * <p>提供电价自定义查询方法，底层由 PriceStandardMapper.xml 中的手写 SQL 驱动，
 * 包含最新生效电价查询（子查询）和按类别的聚合统计。</p>
 */
@Service
public class PriceStandardService extends BaseServiceImpl<PriceStandard> implements BaseService<PriceStandard> {

    /** 允许排序的列（白名单，防止 ORDER BY 注入与未知列报错） */
    private static final Set<String> SORTABLE = Set.of("id", "electricity_category", "base_price", "effective_date", "create_time", "update_time");

    private final PriceStandardMapper priceMapper;

    public PriceStandardService(PriceStandardMapper mapper) {
        super(mapper);
        this.priceMapper = mapper;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<PriceStandard> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        String electricityCategory = conditions.get("electricityCategory");
        LocalDate effectiveDate = conditions.containsKey("effectiveDate") && !conditions.get("effectiveDate").isEmpty()
                ? LocalDate.parse(conditions.get("effectiveDate")) : null;
        boolean hasConditions = (electricityCategory != null && !electricityCategory.isEmpty()) || effectiveDate != null;
        if (!hasConditions) {
            return super.pageList(pageDTO, conditions);
        }
        PageQuery query = resolvePageQuery(pageDTO);
        List<PriceStandard> records = priceMapper.selectByPageWithConditions(
                electricityCategory, effectiveDate, query.offset(), query.size(), query.sortField(), query.sortOrder());
        long total = priceMapper.countByPageWithConditions(electricityCategory, effectiveDate);
        PageResult<PriceStandard> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /** 分页查询电价标准 */
    public List<Map<String, Object>> queryPricePage(String electricityCategory, String pricingMethod,
                                                     LocalDate effectiveDate) {
        return priceMapper.selectPricePage(electricityCategory, pricingMethod, effectiveDate);
    }

    /** 查询各用电类别最新生效电价（子查询取 MAX 日期） */
    public List<Map<String, Object>> queryEffectivePrices(LocalDate queryDate) {
        return priceMapper.selectEffectivePrices(queryDate);
    }

    /** 统计各用电类别价格概况 */
    public List<Map<String, Object>> queryPriceStatistics() {
        return priceMapper.selectPriceStatistics();
    }

    /** 动态更新电价 */
    public int updatePriceSelective(PriceStandard price) {
        return priceMapper.updatePriceSelective(price);
    }
}
