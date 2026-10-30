package com.power.device.service;

import com.power.common.cache.CacheTemplate;
import com.power.common.cache.DistributedLock;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import com.power.common.util.JsonSupport;
import com.power.device.mapper.SmartMeterMapper;
import com.power.device.pojo.SmartMeter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能电表业务实现
 *
 * <p>本类承载两类"非 CRUD"的关注点，也是整个 device 服务里缓存与并发控制的主战场：</p>
 * <ul>
 *   <li><b>电表状态查询走缓存</b>：电表状态是典型的"读多写少 + 高频轮询"数据
 *       （大屏、列表页、告警判定都会读），适合 Cache Aside。空结果也缓存短 TTL，防穿透。</li>
 *   <li><b>状态变更加分布式锁</b>：同一个电表被两个终端同时改状态（现场手持机 + 后台）
 *       时，若不做互斥，"读取-校验-写入"之间会互相覆盖。用 Redisson 锁把这段临界区串起来，
 *       且用看门狗保证长事务不会被提前释放。</li>
 * </ul>
 */
@Slf4j
@Service
public class SmartMeterService extends BaseServiceImpl<SmartMeter> implements BaseService<SmartMeter> {

    /** 电表详情缓存 key 前缀 */
    private static final String CACHE_KEY_METER = "power:meter:";
    /** 电表详情缓存基础 TTL */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    /** 电表更新锁前缀 */
    private static final String LOCK_KEY_METER = "power:lock:meter:";
    /** 电表查询回源锁前缀（与更新锁区分：更新锁保护写，查询锁只削峰读） */
    private static final String LOCK_KEY_METER_QUERY = "power:lock:meter:query:";
    /** 查询回源抢锁最长等待秒数 */
    private static final long LOCK_WAIT_SECONDS = 2L;

    /** 允许排序的列：电表表可按安装日期、型号排序 */
    private static final Set<String> SORTABLE = Set.of(
            "id", "model", "install_date", "bind_date", "create_time", "update_time");

    private final SmartMeterMapper meterMapper;
    private final CacheTemplate cacheTemplate;
    private final DistributedLock distributedLock;

    public SmartMeterService(SmartMeterMapper mapper,
                             CacheTemplate cacheTemplate,
                             DistributedLock distributedLock) {
        super(mapper);
        this.meterMapper = mapper;
        this.cacheTemplate = cacheTemplate;
        this.distributedLock = distributedLock;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    @Override
    public PageResult<SmartMeter> pageList(PageDTO pageDTO, Map<String, String> conditions) {
        String model = conditions.get("model");
        String meterStatus = conditions.get("meterStatus");
        boolean hasConditions = (model != null && !model.isEmpty())
                || (meterStatus != null && !meterStatus.isEmpty());
        if (!hasConditions) {
            return super.pageList(pageDTO, conditions);
        }

        PageQuery query = resolvePageQuery(pageDTO);
        List<SmartMeter> records = meterMapper.selectByPageWithConditions(
                model, meterStatus, query.offset(), query.size(), query.sortField(), query.sortOrder());
        long total = meterMapper.countByPageWithConditions(model, meterStatus);

        PageResult<SmartMeter> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / query.size()));
        result.setCurrent(query.page());
        result.setSize(query.size());
        result.setRecords(records);
        return result;
    }

    /**
     * 查询电表详情（含所属客户信息）—— Cache Aside + 击穿保护
     *
     * <p>电表状态是大屏与列表页的高频读取对象，属于热点数据。
     * 这里与 {@code PowerEquipmentService} 采用同一套写法：</p>
     * <ol>
     *   <li>纯读缓存，命中即返回；</li>
     *   <li>未命中则抢分布式锁，只有抢到的线程回源并写回，避免过期瞬间的并发打库；</li>
     *   <li>抢不到锁时降级直接查库，保证接口不因锁竞争而失败。</li>
     * </ol>
     */
    public Map<String, Object> queryMeterWithCustomer(Long id) {
        if (id == null) {
            return null;
        }
        String key = CACHE_KEY_METER + id;

        String cached = cacheTemplate.get(key);
        if (cached != null) {
            return JsonSupport.fromJson(cached);
        }

        String lockKey = LOCK_KEY_METER_QUERY + id;
        try {
            return distributedLock.tryExecuteWithLock(lockKey, LOCK_WAIT_SECONDS, () -> {
                // 双重检查：别的线程可能刚写完缓存
                String again = cacheTemplate.get(key);
                if (again != null) {
                    return JsonSupport.fromJson(again);
                }
                Map<String, Object> data = meterMapper.selectMeterWithCustomer(id);
                // data 为 null 时 put 写空值占位，挡住对不存在 ID 的穿透
                cacheTemplate.put(key, data == null ? null : JsonSupport.toJson(data), CACHE_TTL);
                return data;
            });
        } catch (IllegalStateException e) {
            log.debug("抢锁失败，降级为直接查库 meterId={}", id);
            return meterMapper.selectMeterWithCustomer(id);
        }
    }

    /** 分页查询电表（多条件动态过滤 + LEFT JOIN） */
    public List<Map<String, Object>> queryMeterPage(String model, String meterStatus,
                                                     Long customerId, String installLocation) {
        return meterMapper.selectMeterPage(model, meterStatus, customerId, installLocation);
    }

    /** 统计电表整体概况 */
    public Map<String, Object> queryMeterStatistics() {
        return meterMapper.selectMeterStatistics();
    }

    /** 按型号分组统计各状态电表数量 */
    public List<Map<String, Object>> queryMetersByStatusGroupByModel() {
        return meterMapper.selectMetersByStatusGroupByModel();
    }

    /**
     * 更新电表：加分布式锁串行化，提交后延迟双删缓存
     *
     * <p>为什么这里必须加锁：状态变更前可能有校验（如"故障表不能直接置为正常，需先检定"），
     * 校验与写入之间若被另一个请求插入，就会出现"两块都不能用"或"校验被绕过"的结果。</p>
     *
     * @param meter 待更新电表（按非空字段更新）
     * @return 影响行数
     */
    public int updateMeterSelective(SmartMeter meter) {
        if (meter == null || meter.getId() == null) {
            throw new IllegalArgumentException("更新电表时 id 不能为空");
        }
        String lockKey = LOCK_KEY_METER + meter.getId();
        int rows = distributedLock.tryExecuteWithLock(lockKey, 3L, () -> {
            int affected = meterMapper.updateMeterSelective(meter);
            if (affected > 0) {
                // 锁内先删一次，锁外再做延迟双删；锁保证同一时刻只有一个写者，
                // 缓存最终不会残留旧值
                cacheTemplate.evict(CACHE_KEY_METER + meter.getId());
            }
            return affected;
        });
        if (rows > 0) {
            cacheTemplate.deleteAfterCommit(CACHE_KEY_METER + meter.getId());
        }
        return rows;
    }

    /** 新增电表后清理分页与统计类缓存（统计不缓存，此处仅作语义占位） */
    @Override
    public boolean create(SmartMeter entity) {
        return super.create(entity);
    }
}
