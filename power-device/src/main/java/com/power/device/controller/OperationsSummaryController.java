package com.power.device.controller;

import com.power.common.pojo.Result;
import com.power.common.security.DataScope;
import com.power.device.mapper.OperationsSummaryMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运维汇总控制器
 *
 * <p><b>为什么要单独一个"聚合"端点</b>：原来的总览页要展示设备数、变电站在线率、
 * 电表正常率三组数字，前端侧就要被告警服务连续调用 device 服务三次
 * （/equipment/statistics、/substation/statistics、/meter/statistics），
 * 三次串行 HTTP，任一次慢都会拖住整个页面。</p>
 *
 * <p>这三组数字都在 <b>同一个库</b> 里，完全可以在一条 SQL 里用标量子查询一次算完。
 * 这是"微服务拆得过细"最典型的症状——数据本来是一家的，却因为分了服务而被迫远程往返。
 * 把聚合放到数据所在的这一侧，调用方一次拿全。</p>
 *
 * <p>客户角色不可见：设备台账是内部资产，与用电户无关。</p>
 */
@RestController
@RequestMapping("/api/operations")
public class OperationsSummaryController {

    private final OperationsSummaryMapper summaryMapper;

    public OperationsSummaryController(OperationsSummaryMapper summaryMapper) {
        this.summaryMapper = summaryMapper;
    }

    /** 一次查询返回设备/变电站/电表三类汇总 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        // 注释里说了"客户不可见"，这里就必须真拦：设备台账属于内部资产，
        // 客户账号即便拿到这个 URL 也不该看到变电站数量与在运率
        if (DataScope.isCustomer()) {
            throw new DataScope.ForbiddenException("设备运行数据仅对运维与管理人员开放");
        }
        return Result.success(summaryMapper.selectOperationsSummary());
    }
}
