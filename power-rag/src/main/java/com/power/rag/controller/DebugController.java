package com.power.rag.controller;

import jakarta.servlet.http.HttpServletResponse;
import com.power.rag.retrieval.HybridRetriever;
import com.power.rag.service.Stats;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试/评测用的检索预览接口：只检索不调大模型，方便前端页面边看边调参。
 */
@RestController
public class DebugController {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final HybridRetriever hybrid;
    private final Stats statsService;

    private final JdbcTemplate jdbc;

    public DebugController(VectorStore vectorStore, ChatClient chatClient,
                           HybridRetriever hybrid, DataSource dataSource, Stats statsService) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.hybrid = hybrid;
        this.jdbc = new JdbcTemplate(dataSource);
        this.statsService = statsService;
    }

    // 页面放在 static/ui.html，由 Spring 直接按静态资源返回，不走 @RestController 的序列化
    @GetMapping("/")
    public void root(HttpServletResponse resp) throws Exception {
        resp.sendRedirect("/ui.html");
    }

    /**
     * 一眼看全：库里有几块、分别来自哪些文件（确认入库没漏、没重复灌）,
     * 外加缓存命中率与累计 token（11.18 加的，合并在这里避免两个 /stats 打架）。
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Integer blocks = jdbc.queryForObject("select count(*) from vector_store", Integer.class);
        List<Map<String, Object>> sources = jdbc.queryForList(
                "select metadata->>'source' as \"source\", count(*) as \"blocks\" "
                        + "from vector_store group by metadata->>'source' order by 2 desc, 1");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("blocks", blocks == null ? 0 : blocks);
        out.put("sources", sources);
        out.putAll(statsService.snapshot());   // requests / hit / miss / hitRate / *Tokens
        return out;
    }

    /** 只检索，不调 LLM，用于调到合适的分块/条数 */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String question,
                                      @RequestParam(defaultValue = "5") int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Document d : hits) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("source", String.valueOf(d.getMetadata().get("source")));
            r.put("score", d.getScore() == null ? null : String.format("%.4f", d.getScore()));
            String t = d.getText() == null ? "" : d.getText();
            r.put("text", t.length() > 400 ? t.substring(0, 400) + "…" : t);
            rows.add(r);
        }
        return Map.of("count", rows.size(), "hits", rows);
    }

    /** 问答（带出处）：融合 + 重排，重排分低于阈值就直接拒答，不浪费大模型调用 */
    @GetMapping("/ask2")
    public Map<String, Object> ask2(@RequestParam String question,
                                    @RequestParam(defaultValue = "5") int topK) {
        HybridRetriever.Result r = hybrid.retrieve(question, topK);

        // 重排分是「这段资料能不能回答这个问题」的模型判断，低于阈值说明库里确实没有，
        // 这时候调大模型只会让它硬编，直接返回固定话术更安全
        if (r.shouldReject()) {
            return Map.of(
                    "answer", "知识库中没有相关内容。",
                    "sources", List.of(),
                    "rejected", true,
                    "topScore", r.chunks().isEmpty() ? "-" : String.format("%.3f", r.chunks().get(0).score()));
        }

        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < r.chunks().size(); i++) {
            HybridRetriever.Chunk ch = r.chunks().get(i);
            ctx.append("【资料").append(i + 1).append("｜").append(ch.source()).append("】\n")
               .append(ch.text()).append("\n\n");
        }
        String answer = chatClient.prompt()
                .system("""
                        你是电力运维助手。严格按下面规则回答：
                        1. 只能依据【资料】中的内容回答，不得使用资料以外的知识。
                        2. 资料里没写到的问题，直接说"这条规程里没写到"，不要推测、不要凑答案。
                        3. 分点用"- "或"1. "，关键数值用**加粗**；不要写#标题、不要画表格、
                           不要用---分隔线，回答是放在聊天框里看的。
                        4. 回答末尾用"出处：资料N"标注依据，N 是资料编号。
                        """)
                .user("【资料】\n" + ctx + "\n【问题】" + question)
                .call()
                .content();

        return Map.of(
                "answer", answer,
                "sources", r.chunks().stream().map(HybridRetriever.Chunk::source).toList(),
                "rejected", false,
                "topScore", String.format("%.3f", r.chunks().get(0).score()));
    }

    /** 把页面上写好的一道题追加进评测集 */
    @PostMapping("/eval/add")
    public Map<String, Object> addQuestion(@RequestBody Map<String, String> body) throws Exception {
        String q = body.getOrDefault("question", "").trim();
        String expect = body.getOrDefault("expect", "").trim();
        String type = body.getOrDefault("type", "").trim();
        if (q.isEmpty() || expect.isEmpty() || type.isEmpty()) {
            return Map.of("ok", false, "msg", "问题/期望出处/类型都不能为空");
        }
        if (q.contains(",") || expect.contains(",")) {
            return Map.of("ok", false, "msg", "不要用英文逗号，改用中文逗号或顿号");
        }
        Path p = Path.of("data/eval/questions.csv");
        Files.writeString(p, q + "," + expect + "," + type + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        long n = Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank() && !l.startsWith("\uFEFF") && !l.startsWith("问题"))
                .count();
        return Map.of("ok", true, "msg", "已追加，当前共 " + n + " 题", "total", n);
    }
}
