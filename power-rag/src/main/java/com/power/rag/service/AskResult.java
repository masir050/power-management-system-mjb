package com.power.rag.service;

import java.util.List;
import java.util.Map;

/**
 * /ask 的响应体，也是缓存里存的 JSON。answer 与 sources 必须整体存取，否则会出现答案和出处错配。
 */
public record AskResult(String answer, List<String> sources) {

    public Map<String, Object> toMap() {
        return Map.of(
                "answer", answer == null ? "" : answer,
                "sources", sources == null ? List.of() : sources);
    }
}
