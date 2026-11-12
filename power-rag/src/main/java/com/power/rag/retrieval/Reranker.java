package com.power.rag.retrieval;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 重排（Cross-Encoder）：把 (问题, 资料) 成对喂进模型逐字判断相关性，
 * 解决向量检索「同族文档谁该排第一」靠运气的问题。慢且贵，只对少量候选做。
 */
@Service
public class Reranker {

    private static final String MODEL = "BAAI/bge-reranker-v2-m3";

    private final RestClient http;
    private final String apiUrl;
    private final String apiKey;

    public Reranker(@Value("${spring.ai.openai.embedding.base-url:https://api.siliconflow.cn}") String baseUrl,
                    @Value("${spring.ai.openai.embedding.api-key:}") String apiKey) {
        // 复用 embedding 那把 key，同一个硅基流动账号
        this.apiUrl = baseUrl.replaceAll("/+$", "") + "/v1/rerank";
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(20_000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    /**
     * 相关度（0~1）。这是检索里唯一有绝对含义的分数——向量相似度只表示「离得近」，
     * 重排分才表示「这段资料能不能答这个问题」，所以拒答判据建在它上面。
     */
    public record Scored(String text, double score) {}

    public List<String> rerank(String query, List<String> docs, int topN) {
        return rerankScored(query, docs, topN).stream().map(Scored::text).toList();
    }

    /**
     * 返回带分数的候选，按相关度降序。
     * 失败直接抛、不降级：降级回原顺序会让 key 写错、断网时评测照跑，数字和上一档一模一样。
     */
    @SuppressWarnings("unchecked")
    public List<Scored> rerankScored(String query, List<String> docs, int topN) {
        if (docs.isEmpty()) return List.of();
        if (docs.size() == 1) return List.of(new Scored(docs.get(0), Double.NaN));

        // 不传 return_documents：正文本来就有，只要 index + 分数
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "query", query,
                "documents", docs,
                "top_n", Math.min(topN, docs.size()));

        Map<String, Object> resp;
        try {
            resp = http.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "rerank 调用失败（401/403 多为 key 不对，404 多为路径或模型名不对）：" + e.getMessage(), e);
        }

        if (resp == null || !(resp.get("results") instanceof List<?> results)) {
            throw new IllegalStateException("rerank 返回里没有 results：" + resp);
        }
        List<Scored> out = new ArrayList<>();
        for (Object o : results) {
            Map<String, Object> r = (Map<String, Object>) o;
            Object idx = r.get("index");
            Object sc = r.get("relevance_score");
            if (idx instanceof Number n && n.intValue() >= 0 && n.intValue() < docs.size()) {
                // 接口已按 relevance_score 降序返回，照抄顺序
                out.add(new Scored(docs.get(n.intValue()), sc instanceof Number d ? d.doubleValue() : Double.NaN));
            }
        }
        return out;
    }
}
