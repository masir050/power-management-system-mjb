package com.power.rag.trace;

import java.util.ArrayList;
import java.util.List;

/** 用 ThreadLocal 存当前请求的步骤，工具方法里直接 add，Controller 最后取出来 */
public class Trace {

    public record Step(int no, String tool, String args, String result) {}

    private static final ThreadLocal<List<Step>> HOLDER = ThreadLocal.withInitial(ArrayList::new);

    public static void reset() { HOLDER.get().clear(); }

    public static void add(String tool, String args, String result) {
        List<Step> list = HOLDER.get();
        list.add(new Step(list.size() + 1, tool, args,
                result.length() > 120 ? result.substring(0, 120) + "..." : result));
    }

    public static List<Step> steps() { return List.copyOf(HOLDER.get()); }
}