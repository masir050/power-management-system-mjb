package com.power.rag.controller;

import com.power.rag.service.AnswerCache;
import com.power.rag.service.AskResult;
import com.power.rag.service.RolePrompts;
import com.power.rag.service.Stats;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

    @RestController
public class AskController {

    public final ChatClient chatClient;
    public final VectorStore vectorStore;
    private final AnswerCache cache;
    private final Stats stats;

    public AskController(ChatClient chatClient, VectorStore vectorStore,
                         AnswerCache cache, Stats stats) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.cache = cache;
        this.stats = stats;
    }//注入

    /**
     * 缓存只包这一层：question 相同就同 key，连向量检索都省掉（embedding 也是要花钱的）。
     * q 是 question 的别名——手册里的 curl 写的是 ?q=，这里兼容一下，免得 400。
     * role 决定用哪套回答人设（用电用户/运维/管理员），system 是 key 的一部分，三种角色答案天然隔离。
     */
    @GetMapping("/ask")
    public Map<String, Object> ask(@RequestParam(required = false) String question,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false) String role) {
        String query = (question != null && !question.isBlank()) ? question : q;
        if (query == null || query.isBlank()) {
            return Map.of("error", "缺少参数：question（别名 q）");
        }
        String sys = RolePrompts.of(role);
        return cache.getOrLoad(sys, query, () -> doAsk(sys, query)).toMap();
    }

    /** 真正花钱的那段：检索 + 调模型 + 记 token。只在缓存未命中时执行 */
    private AskResult doAsk(String sys, String question) {
        //1.检索
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(5).build());

        //2.拼接上下文
        String context = IntStream.range(0, hits.size())
                .mapToObj(i -> "【资料】" + (i + 1) + "|" + hits.get(i).getText())
                .collect(Collectors.joining("\n"));

        //3.约束模型：只依据给资料、没有就说没有。
        //  用 chatResponse() 而不是 content()，为了拿到 usage 元数据
        ChatResponse resp = chatClient.prompt()
                .system(sys)
                .user("【资料】\n" + context + "\n\n【问题】" + question)
                .call()
                .chatResponse();

        recordUsage(resp);

        String answer = "";
        if (resp != null && resp.getResult() != null && resp.getResult().getOutput() != null) {
            String t = resp.getResult().getOutput().getText();
            answer = t == null ? "" : t;
        }

        List<String> sources = hits.stream()
                .map(h -> String.valueOf(h.getMetadata().get("source")))
                .toList();
        return new AskResult(answer, sources);
    }

    private void recordUsage(ChatResponse resp) {
        if (resp == null) return;
        ChatResponseMetadata md = resp.getMetadata();
        if (md == null) return;
        Usage u = md.getUsage();
        if (u == null) return;
        stats.addUsage(u.getPromptTokens(), u.getCompletionTokens());
    }
}
