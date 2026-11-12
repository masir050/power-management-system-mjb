package com.power.rag.controller;

import com.power.rag.retrieval.Bm25Index;
import com.power.rag.retrieval.HybridRetriever;
import com.power.rag.retrieval.Rrf;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检索评测：只算「有没有捞对块」，不调大模型。题库 data/eval/questions.csv（问题,期望出处,类型）。
 *
 * <pre>
 * mode=vector      向量检索，按余弦相似度判
 * mode=bm25        关键词检索，只判命中
 * mode=rrf         两路各取 10 融合，只判命中
 * mode=rrf-rerank  融合后 Cross-Encoder 重排取前 3，用重排分判拒答
 * </pre>
 *
 * 只有 vector 和 rrf-rerank 能判拒答：判拒答要有「分数低＝没把握」的绝对判据，
 * BM25 与 RRF 的分数只表达相对高低。
 */
@RestController
public class EvalController {

    /** 向量相似度的拒答阈值。实测两类分数几乎重叠，这道闸门一直是 0/N，留着只作对照 */
    private static final double VECTOR_REJECT_THRESHOLD = 0.5;

    // 用 List 而不是 Set.of：Set.of 的迭代顺序每次都可能变，报错信息里的顺序跟着乱跳
    private static final List<String> MODES = List.of("vector", "bm25", "rrf", "rrf-rerank");

    private final VectorStore vectorStore;
    private final Bm25Index bm25Index;
    private final HybridRetriever hybrid;

    public EvalController(VectorStore vectorStore, Bm25Index bm25Index, HybridRetriever hybrid) {
        this.vectorStore = vectorStore;
        this.bm25Index = bm25Index;
        this.hybrid = hybrid;
    }

    @GetMapping("/eval")
    public Map<String, Object> eval(@RequestParam(defaultValue = "vector") String mode) throws Exception {
        if (!MODES.contains(mode)) {
            return Map.of("err", "mode 只能是 " + String.join(" / ", MODES) + "，收到：" + mode);
        }
        boolean bm = "bm25".equals(mode);
        boolean rerankMode = "rrf-rerank".equals(mode);
        boolean fuse = "rrf".equals(mode) || rerankMode;   // 两种融合模式共用「不能判拒答」之外的处理
        double rejectThreshold = rerankMode ? HybridRetriever.REJECT_THRESHOLD : VECTOR_REJECT_THRESHOLD;

        List<List<String>> questions = readQuestions();

        int total = 0, hit1 = 0, hit3 = 0;
        List<String> failedQ = new ArrayList<>();
        int negTotal = 0, negOk = 0, emptyHit = 0;
        int rerankChanged = 0;          // 重排把融合的第一名换掉的题数，用来看重排到底有没有干活
        double overlapSum = 0;          // 两路共同捞到的块数，用来确认融合真的在「融合」而不是「拼接」
        int overlapCnt = 0;
        List<Double> posScores = new ArrayList<>();
        List<Double> negScores = new ArrayList<>();
        List<Map<String, Object>> detail = new ArrayList<>();

        for (List<String> c : questions) {
            String q = c.get(0), expect = c.get(1), type = c.get(2);

            List<String> srcs;
            double top1Score;

            // 单题失败不该让整轮评测作废：网络抖一次就丢掉 99 道题的统计，等于白跑。
            // 但也不能静默——失败题必须出现在返回里，否则会把「没跑到」当成「没命中」。
            try {
            if (bm) {
                List<Bm25Index.Hit> hits = bm25Index.search(q, 3);
                srcs = hits.stream().map(Bm25Index.Hit::source).toList();
                top1Score = hits.isEmpty() ? 0 : hits.get(0).score();

            } else if (rerankMode) {
                // 融合 + 重排走正式主链路，和 /ask2 用的是同一套代码
                HybridRetriever.Result r = hybrid.retrieve(q, HybridRetriever.FUSE_DEPTH, 3);
                srcs = r.chunks().stream().map(HybridRetriever.Chunk::source).toList();
                top1Score = r.chunks().isEmpty() ? 0 : r.chunks().get(0).score();
                overlapSum += r.overlap();
                overlapCnt++;
                if (r.fusedTop1Source() != null && !srcs.isEmpty() && !srcs.get(0).equals(r.fusedTop1Source())) {
                    rerankChanged++;
                }

            } else if (fuse) {
                // 纯融合对照：两路各取 FUSE_DEPTH，融合后只要前 3，不重排
                List<Bm25Index.Hit> vec = vectorHits(q, HybridRetriever.FUSE_DEPTH);
                List<Bm25Index.Hit> kw = bm25Index.search(q, HybridRetriever.FUSE_DEPTH);

                // 诊断用：若这个均值长期是 0，说明两边的 key 对不上，RRF 等于把两份列表首尾相接
                Set<String> kwKeys = new HashSet<>();
                kw.forEach(h -> kwKeys.add(keyOf(h)));
                overlapSum += vec.stream().map(EvalController::keyOf).filter(kwKeys::contains).count();
                overlapCnt++;

                List<Bm25Index.Hit> fused = Rrf.merge(vec, kw, HybridRetriever.RRF_K, 3);
                srcs = fused.stream().map(Bm25Index.Hit::source).toList();
                top1Score = fused.isEmpty() ? 0 : fused.get(0).score();

            } else {
                List<Document> hits = vectorStore.similaritySearch(
                        SearchRequest.builder().query(q).topK(3).build());
                srcs = hits.stream()
                        .map(h -> String.valueOf(h.getMetadata().get("source")))
                        .toList();
                top1Score = hits.isEmpty() || hits.get(0).getScore() == null ? 0 : hits.get(0).getScore();
            }
            } catch (Exception ex) {
                failedQ.add(q);
                Map<String, Object> bad = new LinkedHashMap<>();
                bad.put("问题", q);
                bad.put("期望", expect);
                bad.put("类型", type);
                bad.put("错误", String.valueOf(ex.getMessage()));
                detail.add(bad);
                continue;
            }

            total++;
            boolean ok1 = !srcs.isEmpty() && srcs.get(0).equals(expect);
            boolean ok3;

            if (isNeg(expect)) {
                negTotal++;
                // 只有「有绝对含义的分数」才判得了拒答：向量相似度 0~1、重排相关度 0~1。
                // BM25 加权和、RRF 名次和只表达相对高低，只能统计「一条都没捞到」。
                boolean canJudge = !bm && (!fuse || rerankMode);
                if (!canJudge) {
                    ok3 = false;
                    if (srcs.isEmpty()) emptyHit++;
                } else {
                    negScores.add(top1Score);
                    ok3 = top1Score < rejectThreshold;
                    if (ok3) negOk++;
                }
            } else {
                posScores.add(top1Score);
                ok3 = srcs.contains(expect);
            }

            // 命中率只统计正例：否定题「判对」是拒答，不是命中，混进来会把分子撑过分母
            if (!isNeg(expect)) {
                if (ok3) hit3++;
                if (ok1) hit1++;
            }

            detail.add(row(q, expect, type, srcs, top1Score, ok3));
        }

        int pos = total - negTotal;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("mode", mode);
        res.put("总数", total);
        res.put("失败题数", failedQ.size());
        if (!failedQ.isEmpty()) res.put("失败题", failedQ);
        res.put("正例数", pos);
        res.put("top3命中", hit3);
        res.put("top1命中", hit1);
        res.put("否定类总数", negTotal);
        res.put("top3命中率", pct(hit3, pos));
        res.put("top1命中率", pct(hit1, pos));

        if (rerankMode) {
            res.put("两路重叠块数均值", overlapCnt == 0 ? "-" : String.format("%.1f", overlapSum / overlapCnt));
            res.put("重排改写top1题数", rerankChanged);
            res.put("否定类拒答正确", negOk);
            res.put("拒答阈值", String.format("%.2f", HybridRetriever.REJECT_THRESHOLD));
            res.put("正例top1重排分均值", avg(posScores));
            res.put("否定类top1重排分均值", avg(negScores));
            res.put("说明", "重排分是「这段资料能不能回答这个问题」的模型判断，有绝对含义，可以当拒答闸门");
        } else if (fuse) {
            res.put("两路重叠块数均值", overlapCnt == 0 ? "-" : String.format("%.1f", overlapSum / overlapCnt));
            res.put("说明", "融合分 = Σ1/(60+名次)，只表达相对排名，没有绝对含义，不能用来判拒答");
        } else if (bm) {
            res.put("否定类零命中", emptyHit);
            res.put("说明", "BM25 分数是关键词加权和，不能与向量分数比较，也不参与拒答判定");
        } else {
            res.put("否定类拒答正确", negOk);
            res.put("拒答阈值", String.format("%.2f", VECTOR_REJECT_THRESHOLD));
            res.put("正例top1分数均值", avg(posScores));
            res.put("否定类top1分数均值", avg(negScores));
        }

        res.put("明细", detail);
        return res;
    }

    /** 兼容旧入口：等价于 /eval?mode=bm25 */
    @GetMapping("/eval/bm25")
    public Map<String, Object> evalBm25() throws Exception {
        return eval("bm25");
    }

    /** 纯向量检索结果包成统一的 Hit，供纯融合对照使用 */
    private List<Bm25Index.Hit> vectorHits(String q, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(topK).build())
                .stream()
                .map(d -> new Bm25Index.Hit(
                        String.valueOf(d.getMetadata().get("source")),
                        d.getText(),
                        d.getScore() == null ? 0 : d.getScore()))
                .toList();
    }

    /** 块的唯一身份：出处 + 内容。与 HybridRetriever 里那份必须一致，否则重叠统计会假性归零 */
    private static String keyOf(Bm25Index.Hit h) {
        return h.source() + "|" + h.text();
    }

    /** 读 csv：跳过表头、空行、问题为空的行，返回 [问题, 期望出处, 类型] */
    private List<List<String>> readQuestions() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("data/eval/questions.csv"), StandardCharsets.UTF_8);
        List<List<String>> out = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("\uFEFF") || line.startsWith("问题")) {
                continue;
            }
            String[] c = line.split(",", -1);
            if (c.length < 2 || c[0].isBlank()) {
                continue;
            }
            out.add(List.of(c[0].trim(), c[1].trim(), c.length > 2 ? c[2].trim() : ""));
        }
        return out;
    }

    private boolean isNeg(String expect) {
        return "NONE".equalsIgnoreCase(expect);
    }

    private Map<String, Object> row(String q, String expect, String type,
                                    List<String> srcs, double score, boolean ok) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("问题", q);
        row.put("期望", expect);
        row.put("类型", type);
        row.put("top3", srcs);
        row.put("top1score", Double.isNaN(score) ? "-" : String.format("%.3f", score));
        row.put("命中", ok);
        return row;
    }

    private String pct(int a, int b) {
        return b == 0 ? "-" : String.format("%.1f%%", a * 100.0 / b);
    }

    private String avg(List<Double> l) {
        if (l.isEmpty()) return "-";
        return String.format("%.3f", l.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }
}
