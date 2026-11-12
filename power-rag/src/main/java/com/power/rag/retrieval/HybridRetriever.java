package com.power.rag.retrieval;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检索主链路：向量召回 + 关键词召回 → RRF 融合 → Cross-Encoder 重排 → 取前 K 块。
 *
 * <p>只用向量不够：同族文档（《变压器运行规程》与《主变过温告警处置》）在向量空间里本来就很近；
 * 融合只决定哪些块进候选，谁排第一要靠重排成对读（问题, 资料）。</p>
 */
@Component
public class HybridRetriever {

    /** RRF 平滑常数，业界惯用 60 */
    public static final int RRF_K = 60;

    /** 每路各取多少候选，太少两路难重合，太多带噪声 */
    public static final int FUSE_DEPTH = 10;

    /**
     * 拒答阈值。100 题实测：正例 top1 最低 0.516、否定类最高 0.470，窗口只有 0.046，
     * 0.5 落在中间。余量很薄，换语料或换 reranker 必须重标。
     */
    public static final double REJECT_THRESHOLD = 0.5;

    private final VectorStore vectorStore;
    private final Bm25Index bm25Index;
    private final Reranker reranker;

    public HybridRetriever(VectorStore vectorStore, Bm25Index bm25Index, Reranker reranker) {
        this.vectorStore = vectorStore;
        this.bm25Index = bm25Index;
        this.reranker = reranker;
    }

    /** score 是重排相关度（0~1），不是余弦相似度 */
    public record Chunk(String source, String text, double score) {}

    /**
     * @param overlap         两路共同召回的块数，长期为 0 说明身份对不上、融合是假的
     * @param fusedTop1Source 只融合不重排时的第一名，用来判断重排有没有改写排名
     */
    public record Result(List<Chunk> chunks, int overlap, String fusedTop1Source) {
        public boolean shouldReject() {
            return chunks.isEmpty() || chunks.get(0).score() < REJECT_THRESHOLD;
        }
    }

    public Result retrieve(String query, int topK) {
        return retrieve(query, FUSE_DEPTH, topK);
    }

    public Result retrieve(String query, int depth, int topK) {
        List<Bm25Index.Hit> vec = vectorHits(query, depth);
        List<Bm25Index.Hit> kw = bm25Index.search(query, depth);

        // 两路必须用同一个身份算法（出处+内容），否则认不出是同一块
        Set<String> kwKeys = new HashSet<>();
        kw.forEach(h -> kwKeys.add(keyOf(h)));
        int overlap = (int) vec.stream().map(HybridRetriever::keyOf).filter(kwKeys::contains).count();

        List<Bm25Index.Hit> fused = Rrf.merge(vec, kw, RRF_K, depth);

        // 重排只回文本+分数，建「文本 → 出处」反查表
        Map<String, String> text2src = new LinkedHashMap<>();
        for (Bm25Index.Hit h : fused) text2src.putIfAbsent(h.text(), h.source());

        List<Reranker.Scored> kept = reranker.rerankScored(query, new ArrayList<>(text2src.keySet()), topK);

        // 必须按 kept 的顺序取出处：用 fused 过滤的话，元素仍按融合顺序排，重排改了名次也看不出来
        List<Chunk> chunks = new ArrayList<>();
        for (Reranker.Scored s : kept) {
            String src = text2src.get(s.text());
            if (src != null) chunks.add(new Chunk(src, s.text(), s.score()));
        }
        return new Result(chunks, overlap, fused.isEmpty() ? null : fused.get(0).source());
    }

    private List<Bm25Index.Hit> vectorHits(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build())
                .stream()
                .map(d -> new Bm25Index.Hit(
                        String.valueOf(d.getMetadata().get("source")),
                        d.getText(),
                        d.getScore() == null ? 0 : d.getScore()))
                .toList();
    }

    private static String keyOf(Bm25Index.Hit h) {
        return h.source() + "|" + h.text();
    }
}
