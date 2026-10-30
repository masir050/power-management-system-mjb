package com.power.alert.service;

import com.power.alert.feign.DeviceClient;
import com.power.alert.mapper.MaintenanceRecordMapper;
import com.power.alert.pojo.MaintenanceRecord;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.pojo.Result;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检修记录业务实现类
 *
 * <p>SQL 只在本库（power_alert）内 JOIN 人员表；设备信息在 power_device 库，
 * 由 {@link DeviceClient} 远程补全。这是拆库后必须遵守的边界：
 * <b>SQL 里不出现跨库的表名</b>，否则编译能过、一查就报 table doesn't exist。</p>
 */
@Slf4j
@Service
public class MaintenanceRecordService extends BaseServiceImpl<MaintenanceRecord>
        implements BaseService<MaintenanceRecord> {

    /**
     * 允许排序的列白名单
     * <p>这些列最终会拼进 {@code ORDER BY ${sortField}}，必须白名单校验；
     * 不列在这里的字段一律回落到 create_time。</p>
     */
    private static final Set<String> SORTABLE = Set.of(
            "id", "occurrence_time", "completion_time", "repair_hours",
            "repair_priority", "equipment_id", "staff_id", "create_time", "update_time");

    private final MaintenanceRecordMapper recordMapper;
    private final DeviceClient deviceClient;

    public MaintenanceRecordService(MaintenanceRecordMapper mapper, DeviceClient deviceClient) {
        super(mapper);
        this.recordMapper = mapper;
        this.deviceClient = deviceClient;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<MaintenanceRecord> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        String faultType = conditions.get("faultType");
        String repairPriority = conditions.get("repairPriority");
        boolean hasConditions = (faultType != null && !faultType.isEmpty())
                || (repairPriority != null && !repairPriority.isEmpty());
        if (!hasConditions) {
            return super.pageList(pageDTO, conditions);
        }

        PageQuery query = resolvePageQuery(pageDTO);
        List<MaintenanceRecord> records = recordMapper.selectByPageWithConditions(
                faultType, repairPriority, query.offset(), query.size(),
                query.sortField(), query.sortOrder());
        long total = recordMapper.countByPageWithConditions(faultType, repairPriority);

        PageResult<MaintenanceRecord> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /**
     * 查询检修记录详情
     *
     * <p>SQL 只能 JOIN 本库的人员表；设备名与型号在拆库后属于 power_device，
     * 这里通过 {@link DeviceClient} 远程补全。设备取不到时不影响主记录返回。</p>
     */
    public Map<String, Object> queryRecordWithDetails(Long id) {
        Map<String, Object> data = recordMapper.selectRecordWithDetails(id);
        if (data == null) {
            return null;
        }
        Object equipmentId = data.get("equipmentId");
        if (equipmentId instanceof Number n) {
            Map<String, Object> equipment = fetchEquipment(n.longValue());
            data.put("equipmentName", equipment.getOrDefault("equipmentName", "未知设备#" + n));
            data.put("equipmentType", equipment.get("equipmentType"));
        }
        return data;
    }

    /**
     * 查询检修记录列表（多条件动态过滤 + 时间范围）
     * <p>该方法不带分页参数，返回条数由 Mapper 内部固定，避免一次拉全表。</p>
     *
     * <p>设备名需要逐条远程补全——为避免 N+1 次 HTTP 调用，这里按 equipmentId 去重后
     * 再逐个查询，同一条记录里的多条重复设备不会重复调用。</p>
     */
    public List<Map<String, Object>> queryRecordPage(String faultType, String repairPriority,
                                                     Long equipmentId, Long staffId,
                                                     LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> rows = recordMapper.selectRecordPage(
                faultType, repairPriority, equipmentId, staffId, startTime, endTime);
        fillEquipmentNames(rows, "equipmentId", "equipmentName");
        return rows;
    }

    /** 统计检修记录概况 */
    public Map<String, Object> queryRecordStatistics() {
        return recordMapper.selectRecordStatistics();
    }

    /** 按故障类型分组统计 */
    public List<Map<String, Object>> queryRecordGroupByFaultType() {
        return recordMapper.selectRecordGroupByFaultType();
    }

    /** 查询近期告警（设备名远程补全） */
    public List<Map<String, Object>> queryRecentAlerts(int limit) {
        // 限制上限，防止前端传 limit=999999 一次拉穿
        int safeLimit = Math.min(Math.max(1, limit), 200);
        List<Map<String, Object>> rows = recordMapper.selectRecentAlerts(safeLimit);
        fillEquipmentNames(rows, "equipmentId", "equipmentName");
        return rows;
    }

    /** 动态更新检修记录 */
    public int updateRecordSelective(MaintenanceRecord record) {
        return recordMapper.updateRecordSelective(record);
    }

    /**
     * 批量补全设备名
     *
     * <p>按设备 ID 去重后逐个远程查询。数据量受 limit 约束（最多 200 条），
     * 且同一设备通常重复出现，实际调用次数远小于行数。
     * 若后续列表量级上来，应改为 device 服务提供批量接口（一次传 ID 列表）。</p>
     */
    private void fillEquipmentNames(List<Map<String, Object>> rows, String idKey, String nameKey) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> cache = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object idVal = row.get(idKey);
            if (!(idVal instanceof Number n)) {
                continue;
            }
            long id = n.longValue();
            String name = cache.computeIfAbsent(id, key -> {
                Object eqName = fetchEquipment(key).get("equipmentName");
                return eqName == null ? "未知设备#" + key : eqName.toString();
            });
            row.put(nameKey, name);
        }
    }

    /** 调 device 服务取设备信息；失败返回空 Map，绝不让降级影响主流程 */
    private Map<String, Object> fetchEquipment(long equipmentId) {
        try {
            Result<Map<String, Object>> result = deviceClient.getEquipment(equipmentId);
            if (result != null && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            log.warn("取设备信息失败 equipmentId={}", equipmentId, e);
        }
        return Map.of();
    }
}
