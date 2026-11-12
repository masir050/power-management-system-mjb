package com.power.rag.tools;

import com.power.rag.trace.Trace;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocTools {

    private final VectorStore vectorStore;

    public DocTools(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "在电力规程知识库中检索相关条款。"
            + "当用户询问操作步骤、限值标准、处置流程、规程依据时使用。返回带出处的原文片段。")
    public String searchDocs(
            @ToolParam(description = "检索用的关键词或短句，不要整段问题") String query) {

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(3).build());

        String result;
        if (hits == null || hits.isEmpty()) {
            result = "知识库中没有相关内容。";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hits.size(); i++) {
                sb.append("【").append(i + 1).append("｜")
                        .append(hits.get(i).getMetadata().get("source")).append("】\n")
                        .append(hits.get(i).getText()).append("\n\n");
            }
            result = sb.toString();
        }
        Trace.add("searchDocs", "query=" + query, result);
        return result;
    }
}