package com.power.rag.controller;

import com.power.rag.tools.DeviceTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool Calling 最小示例：不给文档，让模型自己决定调不调工具（知识在数据库里）。
 * 与 /ask2 的区别是 /ask2 先检索再读文档。
 */
@RestController
public class ToolChatController {

    private final ChatClient chatClient;
    private final DeviceTools deviceTools;

    public ToolChatController(ChatClient.Builder builder, DeviceTools deviceTools) {
        this.chatClient = builder.build();
        this.deviceTools = deviceTools;
    }

    @GetMapping("/tool-chat")
    public String chat(@RequestParam String q) {
        return chatClient.prompt()
                .system("你是电力运维助手。需要设备信息时必须调用工具查询，不要凭记忆回答。")
                .user(q)
                .tools(deviceTools)   // 关键：工具交给模型，调不调、调几次由模型决定
                .call()
                .content();
    }
}
