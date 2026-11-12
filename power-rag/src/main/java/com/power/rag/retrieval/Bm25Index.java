package com.power.rag.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 极简 BM25：启动时把 vector_store 里的块读进内存建索引。
 * 中文按"相邻两字"切，不引入分词器。
 */
@Component
public class Bm25Index {

    private static final double K1 = 1.5, B = 0.75;

    public record Chunk(String source, String text, List<String> terms) {}
    public record Hit(String source, String text, double score) {}

    private final List<Chunk> chunks = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();   // 词 -> 出现在几个块里
    private double avgLen = 1;

    public Bm25Index(JdbcTemplate jdbc) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("select content, metadata from vector_store");
        for (Map<String, Object> row : rows) {
            String text = String.valueOf(row.get("content"));
            String meta = String.valueOf(row.get("metadata"));
            // metadata 是 {"source":"变压器运行规程"}，把值抠出来
            String source = meta.replaceAll(".*\"source\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            List<String> terms = bigram(text);
            chunks.add(new Chunk(source, text, terms));
            for (String t : new HashSet<>(terms)) df.merge(t, 1, Integer::sum);
        }
        if (!chunks.isEmpty()) {
            avgLen = chunks.stream().mapToInt(c -> c.terms().size()).average().orElse(1);
        }
    }

    /** 中文二元切分：去掉空白和标点，取相邻两字。"变压器油温" -> 变压/压器/器油/油温 */
    static List<String> bigram(String s) {
        String clean = s.replaceAll("[\\s\\p{Punct}，。；：、“”（）《》【】]", "");
        List<String> out = new ArrayList<>();
        for (int i = 0; i + 2 <= clean.length(); i++) out.add(clean.substring(i, i + 2));
        return out;
    }

    public int size() {
        return chunks.size();
    }

    /**
     * 返回的分数是关键词加权和，量纲与余弦相似度不同（常见 4~16），
     * 不能拿去和向量分数比大小，也不能套用相似度阈值。
     */
    public List<Hit> search(String query, int topK) {
        List<String> q = bigram(query);
        int n = chunks.size();
        List<Hit> scored = new ArrayList<>();
        for (Chunk c : chunks) {
            Map<String, Integer> tf = new HashMap<>();
            for (String t : c.terms()) tf.merge(t, 1, Integer::sum);
            double score = 0;
            for (String t : q) {
                int f = tf.getOrDefault(t, 0);
                if (f == 0) continue;
                int d = df.getOrDefault(t, 1);
                double idf = Math.log(1 + (n - d + 0.5) / (d + 0.5));
                score += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * c.terms().size() / avgLen));
            }
            if (score > 0) scored.add(new Hit(c.source(), c.text(), score));
        }
        scored.sort(Comparator.comparingDouble(Hit::score).reversed());
        return scored.subList(0, Math.min(topK, scored.size()));
    }
}
