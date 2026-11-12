package com.power.rag.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.power.rag.splitter.Splitter;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 语料入库：data/docs 下的 md → 按小节切块 → 算向量 → 写 pgvector。不幂等，重灌前先清空 vector_store。
 */
@RestController
public class IngestController {

    private final VectorStore vectorStore;

    public IngestController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest() throws Exception {
        List<Document> docs = new ArrayList<>();
        // 工作目录必须是项目根，否则找不到 data/docs
        try (Stream<Path> files = Files.walk(Path.of("data/docs"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                String raw = Files.readString(f);
                // source = 文件名去后缀，随块存库，回答时标出处
                String source = f.getFileName().toString().replace(".md", "");
                for (String chunk : Splitter.byHeading(raw, 800, 80)) {
                    docs.add(new Document(chunk, Map.of("source", source)));
                }
            }
        }
        // 分批调 embedding，单批文本量过大接口会拒收
        for (int i = 0; i < docs.size(); i += 32) {
            vectorStore.add(docs.subList(i, Math.min(i + 32, docs.size())));
        }
        return Map.of("docs", docs.size());
    }
}
