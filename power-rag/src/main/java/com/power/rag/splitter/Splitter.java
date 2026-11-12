package com.power.rag.splitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 markdown 二级标题（##）切块：## 是作者划的语义边界，切出的每块是完整话题。
 * 一节超过 maxLen 时按段落二次切，新块开头保留上一块尾部 overlap 字。
 */
public class Splitter {

    public static final int DEFAULT_MAX_LEN = 800;
    public static final int DEFAULT_OVERLAP = 100;

    /** 只按标题切，不做二次切 */
    public static List<String> byHeading(String md) {
        return byHeading(md, Integer.MAX_VALUE, 0);
    }

    public static List<String> byHeading(String md, int maxLen, int overlap) {
        List<String> out = new ArrayList<>();
        String[] parts = md.split("(?m)^##\\s+");
        String title = "";
        for (int i = 0; i < parts.length; i++) {
            String sec = parts[i].trim();
            if (sec.isEmpty()) {
                continue;
            }
            if (i == 0) {
                // 首段是 # 文档标题：单独存起来，拼到每块开头
                title = sec;
                continue;
            }
            out.addAll(wordWindows(title, sec, maxLen, overlap));
        }
        return out;
    }

    private static List<String> wordWindows(String title, String sec, int maxLen, int overlap) {
        String prefix = title.isEmpty() ? "" : title + "\n\n";

        if (sec.length() <= maxLen) {
            return List.of(prefix + sec);
        }

        List<String> res = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String para : sec.split("\n")) {
            if (cur.length() > 0 && cur.length() + para.length() + 1 > maxLen) {
                res.add(prefix + cur.toString().trim());
                int keep = Math.min(overlap, cur.length());
                cur = new StringBuilder(cur.substring(cur.length() - keep));
            }
            cur.append(para).append('\n');
        }
        if (cur.toString().trim().length() > 0) {
            res.add(prefix + cur.toString().trim());
        }
        return res;
    }
}
