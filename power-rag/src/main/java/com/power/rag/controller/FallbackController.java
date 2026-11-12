package com.power.rag.controller;

import com.power.rag.service.ResilientChat;
import com.power.rag.trace.Trace;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 降级的验收入口：把主 key 改坏 → 重启 → 调这个接口，应该仍能拿到答案，
 * 响应里 trace 的第一条是 llm / main(failed)，第二条是 llm / backup。
 */
@RestController
public class FallbackController {

    private static final String SYSTEM = """
            你是电力设备运维助手。回答控制在三句话以内，不要编造设备数据。
            """;

    private final ResilientChat resilientChat;

    public FallbackController(ResilientChat resilientChat) {
        this.resilientChat = resilientChat;
    }

    @GetMapping("/ask-fb")
    public Map<String, Object> askFb(@RequestParam String q) {
        Trace.reset();
        String answer = resilientChat.ask(SYSTEM, q);
        return Map.of("answer", answer, "trace", Trace.steps());
    }
}
