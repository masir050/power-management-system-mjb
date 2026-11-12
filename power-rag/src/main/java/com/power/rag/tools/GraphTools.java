package com.power.rag.tools;

import com.power.rag.trace.Trace;
import org.neo4j.driver.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class GraphTools {

    private final Driver driver;

    public GraphTools(@Value("${neo4j.uri}") String uri,
                      @Value("${neo4j.username}") String user,
                      @Value("${neo4j.password}") String pwd) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, pwd));
    }

    /** Cypher 是常量，只有 $id 是参数 —— 这条纪律比功能重要 */
    private static final String DOWNSTREAM =
            "MATCH p=(d:Device {id:$id})-[:FEEDS*1..2]->(x) " +
                    "RETURN x.id AS id, x.name AS name, length(p) AS hops ORDER BY hops";

    @Tool(description = "查询某台设备的下游供电设备（最多两跳）。"
            + "当用户询问影响范围、停电波及哪些设备时使用。")
    public String downstream(@ToolParam(description = "设备编号，例如 1") String id) {
        String result = run(DOWNSTREAM, id);
        Trace.add("downstream", "id=" + id, result);
        return result;
    }

    private String run(String cypher, String id) {
        try (Session s = driver.session()) {
            return s.run(cypher, java.util.Map.of("id", id)).list(r ->
                            r.get("id").asString() + " " + r.get("name").asString()
                                    + " (跳数 " + r.get("hops").asInt() + ")")
                    .stream().collect(Collectors.joining("\n"));
        }
    }
}