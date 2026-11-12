package com.power.rag.controller;

import com.power.rag.tools.DeviceTools;
import com.power.rag.tools.DocTools;
import com.power.rag.tools.GraphTools;
import com.power.rag.trace.Trace;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class DiagnoseController {

    private final ChatClient chatClient;
    private final DeviceTools deviceTools;
    private final DocTools docTools;
    private final GraphTools graphTools;

    public DiagnoseController(ChatClient.Builder builder,
                              DeviceTools deviceTools,
                              DocTools docTools,
                              GraphTools graphTools) {
        this.chatClient = builder.build();
        this.deviceTools = deviceTools;
        this.docTools = docTools;
        this.graphTools = graphTools;
    }

    @GetMapping("/diagnose")
    public Map<String, Object> diagnose(@RequestParam String alarm) {
        Trace.reset();
            String answer = chatClient.prompt()
                    .system("""
                    你是电力故障研判助手。按下面的顺序工作，最多调用 6 次工具：
                    1. 从告警文本中识别设备编号；
                    2. queryDevice 确认设备，queryAlarms 看历史告警；
                    3. downstream / upstream 判断影响范围；
                    4. searchDocs 查对应规程的处置条款；
                    5. 输出【研判结论】【影响范围】【处置建议】【规程依据】四段。
                    格式：分点用"- "或"1. "，设备名和关键数值用**加粗**；不要写#标题、不要画表格、
                    不要用---分隔线，回答是放在聊天框里看的。
                    如果 6 步内无法确定设备，直接回答"无法从告警中定位设备"，不要继续尝试。
                    """)
                    .user(alarm)
                    .tools(deviceTools, docTools, graphTools)
                    .call()
                    .content();
            return Map.of("answer", answer, "steps", Trace.steps().size(), "trace", Trace.steps());
    }
}
