package com.power.device.service;

import com.power.common.cache.CacheTemplate;
import com.power.common.cache.DistributedLock;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import com.power.common.util.JsonSupport;
import com.power.device.mapper.PowerEquipmentMapper;
import com.power.device.pojo.PowerEquipment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 电力设备业务实现类
 *
 * <p>设备详情是典型的<b>热点数据</b>：大屏、设备台账、告警判定三处都在读同一个设备，
 * 但设备主数据一天改不了几次。因此详情查询走 Cache Aside 缓存。</p>
 *
 * <h3>三层防护各自解决什么</h3>
 * <ul>
 *   <li><b>穿透</b>：查一个不存在的设备 ID，每次都打到数据库。
 *       由 {@link CacheTemplate#getOrLoad} 对空结果写 60s 空值占位解决。</li>
 *   <li><b>击穿</b>：热点 key 过期的那一瞬间，成百个并发请求同时回源。
 *       由本类的 {@link #queryEquipmentWithSubstation} 抢分布式锁解决——
 *       只有抢到的线程回源，其余线程等待后重读缓存。</li>
 *   <li><b>雪崩</b>：大批 key 同时过期。
 *       由 {@link CacheTemplate} 对 TTL 加 ±10% 抖动解决。</li>
 * </ul>
 *
 * <h3>为什么击穿保护只加在详情上</h3>
 * <p>加锁有代价（一次额外的 Redis 往返 + 可能的等待）。只有真正的热点才值得：
 * 设备详情的 QPS 最高、单 key 热度最集中，才加锁；列表与统计查询不加。
 * 这是有意识的取舍，不是遗漏。</p>
 */
@Slf4j
@Service
public class PowerEquipmentService extends BaseServiceImpl<PowerEquipment> implements BaseService<PowerEquipment> {

    /** 设备详情缓存前缀 */
    private static final String CACHE_KEY_EQUIPMENT = "power:equipment:";
    /** 回源锁前缀（与缓存 key 区分开，避免业务 key 与锁 key 混淆） */
    private static final String LOCK_KEY_EQUIPMENT = "power:lock:equipment:";
    /** 设备详情 TTL：主数据变更极低频，10 分钟是读写比与一致性之间的平衡点 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    /** 抢锁最长等待秒数：超时则降级直接查库，避免热点把线程池堵死 */
    private static final long LOCK_WAIT_SECONDS = 2L;

    /** 允许排序的列：设备列表常按危害等级、出厂日期、投运批次查看 */
    private static final Set<String> SORTABLE = Set.of(
            "id", "equipment_name", "equipment_type", "hazard_level",
            "manufacture_date", "create_time", "update_time");

    private final PowerEquipmentMapper equipmentMapper;
    private final CacheTemplate cacheTemplate;
    private final DistributedLock distributedLock;

    public PowerEquipmentService(PowerEquipmentMapper mapper,
                                 CacheTemplate cacheTemplate,
                                 DistributedLock distributedLock) {
        super(mapper);
        this.equipmentMapper = mapper;
        this.cacheTemplate = cacheTemplate;
        this.distributedLock = distributedLock;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<PowerEquipment> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        String equipmentName = conditions.get("equipmentName");
        String equipmentType = conditions.get("equipmentType");
        String hazardLevel = conditions.get("hazardLevel");
        boolean hasConditions = (equipmentName != null && !equipmentName.isEmpty())
                || (equipmentType != null && !equipmentType.isEmpty())
                || (hazardLevel != null && !hazardLevel.isEmpty());
        if (!hasConditions) {
            return super.pageList(pageDTO, conditions);
        }

        PageQuery query = resolvePageQuery(pageDTO);
        List<PowerEquipment> records = equipmentMapper.selectByPageWithConditions(
                equipmentName, equipmentType, hazardLevel, query.offset(), query.size(),
                query.sortField(), query.sortOrder());
        long total = equipmentMapper.countByPageWithConditions(equipmentName, equipmentType, hazardLevel);

        PageResult<PowerEquipment> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /**
     * 查询设备详情（含所属变电站信息）—— Cache Aside + 击穿保护
     *
     * <p>流程：</p>
     * <pre>
     *   1. 读缓存，命中直接返回（绝大多数请求在这里结束）
     *   2. 未命中：抢 "power:lock:equipment:{id}"，抢到者回源数据库并写回缓存
     *   3. 抢到锁后再做一次双重检查（可能别的线程刚写完）
     *   4. 没抢到锁：降级直接查库，不把锁竞争暴露成 500
     * </pre>
     *
     * <p>第 4 步是<b>刻意</b>的：分布式锁的目的是削峰，不应让拿不到锁的用户看到错误。
     * 宁可让极少数请求穿透，也不能让接口失败。</p>
     *
     * @param id 设备 ID
     * @return 设备详情，不存在时返回 null
     */
    public Map<String, Object> queryEquipmentWithSubstation(Long id) {
        if (id == null) {
            return null;
        }
        String key = CACHE_KEY_EQUIPMENT + id;

        // 1. 纯读缓存（不回源，未命中交给下面的加锁流程）
        String cached = cacheTemplate.get(key);
        if (cached != null) {
            return JsonSupport.fromJson(cached);
        }

        String lockKey = LOCK_KEY_EQUIPMENT + id;
        try {
            // 2/3. 抢锁回源，锁内做双重检查
            return distributedLock.tryExecuteWithLock(lockKey, LOCK_WAIT_SECONDS, () -> {
                String again = cacheTemplate.get(key);
                if (again != null) {
                    return JsonSupport.fromJson(again);
                }
                Map<String, Object> data = equipmentMapper.selectEquipmentWithSubstation(id);
                // data 为 null 时 put 会写空值占位，挡住后续对不存在 ID 的穿透
                cacheTemplate.put(key, data == null ? null : JsonSupport.toJson(data), CACHE_TTL);
                return data;
            });
        } catch (IllegalStateException e) {
            // 4. 抢锁超时/失败：降级直查数据库，保证接口可用
            log.debug("抢锁失败，降级为直接查库 equipmentId={}", id);
            return equipmentMapper.selectEquipmentWithSubstation(id);
        }
    }

    /** 分页查询设备（多条件动态过滤 + LEFT JOIN） */
    public List<Map<String, Object>> queryEquipmentPage(String equipmentName, String equipmentType,
                                                         String hazardLevel, Long substationId,
                                                         String installBatch) {
        return equipmentMapper.selectEquipmentPage(equipmentName, equipmentType, hazardLevel, substationId, installBatch);
    }

    /** 统计设备整体概况 */
    public Map<String, Object> queryEquipmentStatistics() {
        return equipmentMapper.selectEquipmentStatistics();
    }

    /** 按危害等级分组统计 */
    public List<Map<String, Object>> queryEquipmentGroupByHazard() {
        return equipmentMapper.selectEquipmentGroupByHazard();
    }

    /**
     * 动态更新设备：写库后失效缓存
     *
     * <p>顺序必须是「先更新数据库，再删除缓存」，且是"删除"而不是"更新缓存"：</p>
     * <ul>
     *   <li>先删缓存再更新库：删完、库还没更新时，另一个读请求会把<b>旧值</b>写回缓存，
     *       缓存里长期是旧数据；</li>
     *   <li>更新缓存而非删除：并发写 A、B 可能 A 先落库、B 后落库，但 B 先写缓存、A 后写，
     *       最终缓存里留下的是 B 的旧值。</li>
     * </ul>
     *
     * <p>{@code deleteAfterCommit} 会在延迟 500ms 后再删一次（延迟双删），
     * 兜住"读请求在删缓存前读到旧值、又在删缓存后写回"的极端竞态。</p>
     */
    public int updateEquipmentSelective(PowerEquipment equipment) {
        if (equipment == null || equipment.getId() == null) {
            throw new IllegalArgumentException("更新设备时 id 不能为空");
        }
        int rows = equipmentMapper.updateEquipmentSelective(equipment);
        if (rows > 0) {
            cacheTemplate.deleteAfterCommit(CACHE_KEY_EQUIPMENT + equipment.getId());
        }
        return rows;
    }
}
