package com.power.rag.controller;

import com.power.rag.memory.Memory;
import com.power.rag.tools.DeviceTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@RestController
public class ChatStreamController {

    private final ChatClient chatClient;
    private final Memory memory;
    private final DeviceTools deviceTools;

    public ChatStreamController(ChatClient.Builder builder, Memory memory, DeviceTools deviceTools) {
        this.chatClient = builder.build();
        this.memory = memory;
        this.deviceTools = deviceTools;
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> stream(@RequestParam String q) {
        return chatClient.prompt()
                .user(q)
                .stream()
                .content();
    }

    @GetMapping(value = "/chat/mem", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> mem(@RequestParam String sid, @RequestParam String q) {
        List<Message> msgs = new ArrayList<>();
        for (String line : memory.load(sid)) {
            int i = line.indexOf('|');
            String role = line.substring(0, i), text = line.substring(i + 1);
            msgs.add("user".equals(role) ? new UserMessage(text) : new AssistantMessage(text));
        }
        msgs.add(new UserMessage(q));
        memory.save(sid, "user", q);

        StringBuilder sb = new StringBuilder();
        return chatClient.prompt(new Prompt(msgs))
                .tools(deviceTools)
                .stream().content()
                .doOnNext(sb::append)
                .doOnComplete(() -> memory.save(sid, "assistant", sb.toString()));
    }
}
