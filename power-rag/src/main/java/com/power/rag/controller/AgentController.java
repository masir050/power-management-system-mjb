package com.power.rag.controller;

import com.power.rag.tools.DeviceTools;
import com.power.rag.tools.DocTools;
import com.power.rag.trace.Trace;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 11.11：把「查库」和「查规程」合成一个入口。
 * 和 /tool-chat 的区别只有一个：多挂了一个 DocTools，模型可以自己决定查库还是查文档。
 */
@RestController
public class AgentController {

    private final ChatClient chatClient;
    private final DeviceTools deviceTools;
    private final DocTools docTools;

    public AgentController(ChatClient.Builder builder, DeviceTools deviceTools, DocTools docTools) {
        this.chatClient = builder.build();
        this.deviceTools = deviceTools;
        this.docTools = docTools;
    }

    @GetMapping("/agent")
    public Map<String, Object> agent(@RequestParam String q) {
        Trace.reset();   // 必须在 prompt 之前：同一线程被复用时会带着上一次请求的 step
        String answer = chatClient.prompt()
                .system("""
                        你是电力运维研判助手。可用能力：
                        - 设备台账、告警、检修记录：调用工具查，不要凭记忆。
                        - 规程条款：调用 searchDocs 检索，回答必须引用出处。
                        规则：
                        1. 信息不足就继续调工具，不要猜。
                        2. 规程里没写到的地方，直接说"规程里没写到"，不要推测。
                        3. 最终回答分两段：【结论】和【依据】。
                        """)
                .user(q)
                .tools(deviceTools, docTools)
                .call()
                .content();
        return Map.of("answer", answer, "trace", Trace.steps());
    }
    
}
