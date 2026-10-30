package com.power.alert.service;

import com.power.alert.feign.DeviceClient;
import com.power.alert.mapper.AlertMapper;
import com.power.alert.pojo.AlertRecord;
import com.power.common.exception.BusinessException;
import com.power.common.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 告警创建入口（<b>事务外</b>的部分）
 *
 * <p>为什么要把"取设备名"和"写库+登记消息"拆成两个 Bean：</p>
 * <ul>
 *   <li>远程调用不该待在数据库事务里——device 服务慢 3 秒，本地事务就持锁 3 秒，
 *       连接池很快被耗尽；</li>
 *   <li>如果放在同一个类的两个方法里，{@code this.persistAndEnqueue()} 不走 Spring 代理，
 *       方法上的 {@code @Transactional} 会被静默忽略，事务实际不存在。</li>
 * </ul>
 *
 * <p>拆开之后：本类负责<b>事务外</b>的远程补全与参数默认值，
 * {@link AlertTxService} 负责<b>事务内</b>的落库与消息登记，调用关系清晰且事务真实生效。</p>
 */
@Slf4j
@Service
public class AlertService {

    private final DeviceClient deviceClient;
    private final AlertTxService alertTxService;

    public AlertService(DeviceClient deviceClient, AlertTxService alertTxService) {
        this.deviceClient = deviceClient;
        this.alertTxService = alertTxService;
    }

    /**
     * 新增告警
     *
     * @param record 告警（equipmentName 为空时自动从 device 服务取快照）
     * @return 新告警 ID
     */
    public Long createAlert(AlertRecord record) {
        if (record == null || record.getEquipmentId() == null) {
            throw new BusinessException("新增告警时 equipmentId 不能为空");
        }
        if (record.getAlertStatus() == null) {
            record.setAlertStatus(AlertRecord.STATUS_PENDING);
        }
        if (record.getOccurrenceTime() == null) {
            record.setOccurrenceTime(LocalDateTime.now());
        }
        if (record.getEquipmentName() == null || record.getEquipmentName().isBlank()) {
            record.setEquipmentName(fetchEquipmentName(record.getEquipmentId()));
        }
        return alertTxService.persistAndEnqueue(record);
    }

    /**
     * 从设备服务取设备名
     * <p>取不到时返回占位名而不是抛异常：告警本身比设备名更重要，
     * 不能因为 device 服务抖动就让告警创建失败。</p>
     */
    private String fetchEquipmentName(Long equipmentId) {
        try {
            Result<Map<String, Object>> result = deviceClient.getEquipment(equipmentId);
            if (result != null && result.getData() != null) {
                Object name = result.getData().get("equipmentName");
                if (name != null) {
                    return name.toString();
                }
            }
        } catch (Exception e) {
            log.warn("取设备名失败 equipmentId={}，将使用占位名", equipmentId, e);
        }
        return "未知设备#" + equipmentId;
    }
}
