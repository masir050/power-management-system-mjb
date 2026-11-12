package com.power.rag.controller;

import com.power.rag.service.AnswerCache;
import com.power.rag.service.AskResult;
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

    private static final String SYSTEM = """
            你是变电站值班室里的老运行，规程熟、说话像同事之间交代事情。
            1. 只依据【资料】回答，不用资料以外的知识；没有依据的事不推断、不凑答案。
            2. 资料覆盖不到时，用一句人话说清手头这几份规程没写到这块，再说明该往哪类文件里找；
               不要用"未规定""予以""特此"这类公文腔。
            3. 要分点就用"- "或"1. "，关键数字和结论可以用**加粗**；不要写#标题、不要画表格、
               不要用---分隔线，回答是放在聊天框里看的。
            4. 回答末尾用"出处：资料N"标注依据，N 是资料编号。
            """;

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
     */
    @GetMapping("/ask")
    public Map<String, Object> ask(@RequestParam(required = false) String question,
                                   @RequestParam(required = false) String q) {
        String query = (question != null && !question.isBlank()) ? question : q;
        if (query == null || query.isBlank()) {
            return Map.of("error", "缺少参数：question（别名 q）");
        }
        return cache.getOrLoad(SYSTEM, query, () -> doAsk(query)).toMap();
    }

    /** 真正花钱的那段：检索 + 调模型 + 记 token。只在缓存未命中时执行 */
    private AskResult doAsk(String question) {
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
                .system(SYSTEM)
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
