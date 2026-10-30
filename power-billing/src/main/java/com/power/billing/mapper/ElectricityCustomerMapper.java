package com.power.billing.mapper;

import com.power.common.mapper.BaseMapper;
import com.power.billing.pojo.ElectricityCustomer;
import com.power.billing.pojo.ElectricityCustomerVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用电客户 Mapper 接口
 * <p>自定义方法对应 ElectricityCustomerMapper.xml 中的手写 SQL，
 * 包含客户与电表关联查询、按类别和状态的分组统计。</p>
 */
public interface ElectricityCustomerMapper extends BaseMapper<ElectricityCustomer> {

    /** 查询客户及名下所有电表信息（一对多 LEFT JOIN） */
    List<Map<String, Object>> selectCustomerWithMeters(@Param("id") Long id);

    /** 分页查询客户（支持按名称、类别、状态、联系方式动态过滤） */
    List<ElectricityCustomerVO> selectCustomerPage(@Param("customerName") String customerName,
                                                    @Param("electricityCategory") String electricityCategory,
                                                    @Param("electricityStatus") String electricityStatus,
                                                    @Param("contact") String contact);

    /** 带条件的分页查询（返回实体类型，支持排序） */
    List<ElectricityCustomer> selectByPageWithConditions(@Param("customerName") String customerName,
                                                         @Param("electricityCategory") String electricityCategory,
                                                         @Param("electricityStatus") String electricityStatus,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit,
                                                         @Param("sortField") String sortField,
                                                         @Param("sortOrder") String sortOrder);

    /** 带条件的计数查询 */
    long countByPageWithConditions(@Param("customerName") String customerName,
                                    @Param("electricityCategory") String electricityCategory,
                                    @Param("electricityStatus") String electricityStatus);

    /** 统计客户概况（总数、各类别/各状态数量） */
    Map<String, Object> selectCustomerStatistics();

    /** 按用电类别分组统计客户数量及活跃数量 */
    List<Map<String, Object>> selectCustomerGroupByCategory();

    /** 动态更新客户信息 */
    int updateCustomerSelective(ElectricityCustomer customer);
}
