package com.power.alert.service;

import com.power.alert.mapper.AlertMapper;
import com.power.alert.mq.AlertEventPublisher;
import com.power.alert.pojo.AlertRecord;
import com.power.common.exception.BusinessException;
import com.power.common.mq.AlertEvent;
import com.power.common.mq.AlertMqConstants;
import com.power.common.pojo.PageDTO;
import com.power.common.pojo.PageResult;
import com.power.common.service.BaseService;
import com.power.common.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 告警业务实现
 *
 * <p>本类是整个"告警异步化"链路的起点，也是<b>唯一</b>把业务写入与消息登记放进同一事务的地方：</p>
 *
 * <pre>
 *  AlertService.createAlert()         事务外：调 device 补设备名快照
 *      ↓
 *  AlertTxService.persistAndEnqueue() 事务内：insert alert ─┐
 *                                             enqueue msg  ─┴─ 一起提交/回滚
 *      ↓
 *  LocalMessageDispatcher (@Scheduled 每 5s)
 *      抢占 PENDING → 发 RabbitMQ → 标 SENT / 指数退避重试
 *      ↓
 *  AlertEventConsumer (@RabbitListener)
 *      幂等校验 → WebSocket 推送前端
 * </pre>
 *
 * <p><b>为什么单独一个 TxService</b>：{@code @Transactional} 依赖 Spring 代理，
 * 同类内部自调用（{@code this.xxx()}）不经过代理，注解会被静默忽略、事务根本不存在。
 * 把它放到另一个 Bean 里，调用必走代理，事务才真正生效。</p>
 */
@Slf4j
@Service
public class AlertTxService extends BaseServiceImpl<AlertRecord> implements BaseService<AlertRecord> {

    /** 允许排序的列白名单：这些列会拼进 ORDER BY */
    private static final Set<String> SORTABLE = Set.of(
            "id", "occurrence_time", "alert_level", "alert_status",
            "create_time", "update_time", "resolve_time");

    private final AlertMapper alertMapper;
    private final AlertEventPublisher alertEventPublisher;

    public AlertTxService(AlertMapper mapper, AlertEventPublisher alertEventPublisher) {
        super(mapper);
        this.alertMapper = mapper;
        this.alertEventPublisher = alertEventPublisher;
    }

    @Override
    protected boolean isSortableColumn(String column) {
        return SORTABLE.contains(column);
    }

    /**
     * 落库并登记消息（同一事务）
     *
     * <p>两个写操作必须同生共死：只写告警不登记消息，告警永远不会被推送；
     * 只登记消息不写告警，消费端会收到一条查不到对应告警的消息。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long persistAndEnqueue(AlertRecord record) {
        alertMapper.insert(record);

        AlertEvent event = AlertEvent.builder()
                .eventType("ALERT_CREATED")
                .alertId(record.getId())
                .equipmentId(record.getEquipmentId())
                .equipmentName(record.getEquipmentName())
                .faultType(record.getFaultType())
                .faultDescription(record.getAlertDescription())
                .priority(record.getAlertLevel())
                .occurredAt(record.getOccurrenceTime())
                .build();
        alertEventPublisher.enqueue(event, AlertMqConstants.RK_ALERT_CREATED);

        log.info("新增告警 id={} level={} equipment={}",
                record.getId(), record.getAlertLevel(), record.getEquipmentName());
        return record.getId();
    }

    /**
     * 告警状态流转：PENDING → ACKNOWLEDGED / RESOLVED
     *
     * <p>用前置状态做乐观并发控制：两个人同时点"处理完成"，只有第一个生效，
     * 第二个会得到"该告警已被处理"的提示，而不是静默覆盖前者的处理结果。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, String toStatus, Long handlerId, String handleResult) {
        AlertRecord current = alertMapper.selectById(id);
        if (current == null) {
            throw new BusinessException("告警不存在: " + id);
        }
        String fromStatus = current.getAlertStatus();
        if (AlertRecord.STATUS_RESOLVED.equals(fromStatus)) {
            throw new BusinessException("该告警已处理完成，不能重复处理");
        }
        if (AlertRecord.STATUS_RESOLVED.equals(toStatus) && handlerId == null) {
            throw new BusinessException("处理告警时必须指定处理人");
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = alertMapper.updateStatus(id, fromStatus, toStatus, handlerId, handleResult, now);
        if (rows == 0) {
            // 前置校验通过但更新 0 行：并发下状态已被他人改过
            throw new BusinessException("该告警已被他人处理，请刷新后重试");
        }

        AlertEvent event = AlertEvent.builder()
                .eventType("ALERT_RESOLVED")
                .alertId(id)
                .equipmentId(current.getEquipmentId())
                .equipmentName(current.getEquipmentName())
                .faultType(current.getFaultType())
                .priority(current.getAlertLevel())
                .occurredAt(now)
                .build();
        alertEventPublisher.enqueue(event, AlertMqConstants.RK_ALERT_RESOLVED);
        return true;
    }

    /** 告警列表（多条件 + 分页 + 白名单排序） */
    public PageResult<AlertRecord> queryAlertPage(PageDTO pageDTO,
                                                  String alertLevel, String alertStatus, String faultType,
                                                  Long equipmentId, LocalDateTime startTime, LocalDateTime endTime) {
        PageQuery q = resolvePageQuery(pageDTO);
        List<AlertRecord> records = alertMapper.selectAlertPage(alertLevel, alertStatus, faultType,
                equipmentId, startTime, endTime, q.offset(), q.size(), q.sortField(), q.sortOrder());
        long total = alertMapper.countAlertPage(alertLevel, alertStatus, faultType,
                equipmentId, startTime, endTime);

        PageResult<AlertRecord> result = new PageResult<>();
        result.setTotal(total);
        result.setPages((long) Math.ceil((double) total / q.size()));
        result.setCurrent(q.page());
        result.setSize(q.size());
        result.setRecords(records);
        return result;
    }

    public Map<String, Object> queryStatistics() {
        return alertMapper.selectAlertStatistics();
    }

    public List<Map<String, Object>> queryGroupByFaultType() {
        return alertMapper.selectAlertGroupByFaultType();
    }

    public List<Map<String, Object>> queryHourlyTrend(int hours) {
        int safeHours = Math.min(Math.max(1, hours), 168);
        return alertMapper.selectHourlyTrend(LocalDateTime.now().minusHours(safeHours));
    }

    public List<Map<String, Object>> queryRecentAlerts(int limit) {
        return alertMapper.selectRecentAlerts(Math.min(Math.max(1, limit), 100));
    }
}
