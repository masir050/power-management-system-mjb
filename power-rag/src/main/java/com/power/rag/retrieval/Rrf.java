package com.power.rag.retrieval;

import java.util.*;

public class Rrf {

    /**
     * k 取 60 的用处：把"第 1 名"和"第 2 名"的分数差压小，
     * 避免某一路的第 1 名直接把另一路的第 1 名盖过去。
     */
    public static List<Bm25Index.Hit> merge(List<Bm25Index.Hit> a,
                                            List<Bm25Index.Hit> b,
                                            int k, int topK) {
        Map<String, Double> score = new HashMap<>();
        Map<String, Bm25Index.Hit> store = new HashMap<>();
        accumulate(score, store, a, k);
        accumulate(score, store, b, k);
        return score.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    Bm25Index.Hit h = store.get(e.getKey());
                    return new Bm25Index.Hit(h.source(), h.text(), e.getValue());
                })
                .toList();
    }

    private static void accumulate(Map<String, Double> score,
                                   Map<String, Bm25Index.Hit> store,
                                   List<Bm25Index.Hit> hits, int k) {
        for (int i = 0; i < hits.size(); i++) {
            Bm25Index.Hit h = hits.get(i);
            String key = h.source() + "|" + h.text();   // 用"出处+内容"当身份，两路才对得上
            store.put(key, h);
            score.merge(key, 1.0 / (k + i + 1), Double::sum);   // 名次从 1 开始
        }
    }
}