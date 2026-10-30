package com.power.alert.mapper;

import com.power.alert.pojo.LocalMessage;
import com.power.common.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表 Mapper
 * <p>核心方法 {@link #selectPending} 用 {@code FOR UPDATE SKIP LOCKED} 抢占待投递消息，
 * 多个实例同时跑补偿任务时不会重复捞到同一批（见 XML 中的说明）。</p>
 */
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /** 抢占一批待投递消息（多实例安全） */
    List<LocalMessage> selectPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 标记投递成功 */
    int markSent(@Param("messageId") String messageId, @Param("sentTime") LocalDateTime sentTime);

    /** 标记投递失败并退避 */
    int markFailed(@Param("messageId") String messageId,
                   @Param("retryCount") int retryCount,
                   @Param("lastError") String lastError,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime);

    /** 标记为最终失败（超过最大重试次数） */
    int markDead(@Param("messageId") String messageId, @Param("lastError") String lastError);

    /** 按 messageId 查状态（幂等校验用） */
    LocalMessage selectByMessageId(@Param("messageId") String messageId);
}
