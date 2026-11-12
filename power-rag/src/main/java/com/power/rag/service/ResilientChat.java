package com.power.rag.service;

import com.power.rag.config.BackupConfig;
import com.power.rag.trace.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * 带降级的模型调用：主模型（DeepSeek）挂了自动切备用（硅基流动）。
 * 两家都挂就老实返回"服务繁忙"，而不是让用户干等 60 秒再吃 500。
 */
@Service
public class ResilientChat {

    private static final Logger log = LoggerFactory.getLogger(ResilientChat.class);
    private static final String BUSY = "服务暂时繁忙，请稍后重试。";

    private final ChatClient main;
    private final ChatClient backup;
    private final Stats stats;

    public ResilientChat(ChatClient.Builder builder, BackupConfig backupConfig, Stats stats) {
        this.main = builder.build();                      // spring.ai 那套（DeepSeek）
        this.backup = backupConfig.buildBackupClient();   // 手工建（硅基流动）
        this.stats = stats;
    }

    /** 走了哪条路都记进 Trace，排查时不用猜 */
    public String ask(String system, String user) {
        try {
            ChatResponse resp = main.prompt().system(system).user(user).call().chatResponse();
            stats.addUsage(resp);                          // token 也算进 /stats
            String answer = textOf(resp);
            Trace.add("llm", "main", answer);
            return answer;
        } catch (Exception e1) {
            log.warn("[降级] 主模型失败：{}", e1.getMessage());
            Trace.add("llm", "main(failed)", String.valueOf(e1.getMessage()));
            try {
                ChatResponse resp = backup.prompt().system(system).user(user).call().chatResponse();
                stats.addUsage(resp);
                String answer = textOf(resp);
                Trace.add("llm", "backup", answer);
                return answer;
            } catch (Exception e2) {
                log.error("[降级] 备用模型也失败：{}", e2.getMessage());
                Trace.add("llm", "backup(failed)", String.valueOf(e2.getMessage()));
                return BUSY;
            }
        }
    }

    /** 模型偶尔返回空 result，content() 直接 NPE，这里统一兜住 */
    private static String textOf(ChatResponse resp) {
        if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null) {
            return "";
        }
        String t = resp.getResult().getOutput().getText();
        return t == null ? "" : t;
    }
}
