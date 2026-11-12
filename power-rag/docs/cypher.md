# Neo4j 拓扑：建图与查询

> 用途：`GraphTools.downstream` 查「某台设备的下游是谁」，给研判答案补影响范围。
> 库里现状：**6 个 `:Device` 节点、3 条 `:FEEDS` 关系**（2026-11-11 实查）。

## 一、为什么这一段必须造数据

整条链路里，只有「谁给谁供电」这个关系是库里原本没有的——
设备台账、告警、检修记录全在 MySQL 真表里，向量块在 pgvector 里。
拓扑关系没有现成表，只能按设备台账里的上下游逻辑手建，所以单独放在图库。

## 二、节点（6 台，与 MySQL `power_equipment` 对齐）

| id | name | 类型 |
|---|---|---|
| 1 | 圣火令主变压器 | 变压器 |
| 2 | 真武剑主变压器 | 变压器 |
| 3 | 倚天剑断路器 | 断路器 |
| 4 | 屠龙刀隔离开关 | 隔离开关 |
| 5 | 灭绝变压器 | 变压器 |
| 6 | 天鹰刀断路器 | 断路器 |

`id` 存成**字符串**（`'1'` 不是 `1`）。`GraphTools` 里是 `{id:$id}` 字符串匹配，
用整数查会静默返回空——不报错，只是查不到，这个坑最容易踩。

## 三、建图语句（已在库里执行过，重建时才用）

```cypher
// 1) 唯一约束：同一个 id 不会被灌成两个节点
CREATE CONSTRAINT device_id IF NOT EXISTS
FOR (d:Device) REQUIRE d.id IS UNIQUE;

// 2) 节点
UNWIND [
  {id:'1', name:'圣火令主变压器'},
  {id:'2', name:'真武剑主变压器'},
  {id:'3', name:'倚天剑断路器'},
  {id:'4', name:'屠龙刀隔离开关'},
  {id:'5', name:'灭绝变压器'},
  {id:'6', name:'天鹰刀断路器'}
] AS row
MERGE (d:Device {id: row.id})
SET d.name = row.name;

// 3) 供电关系：主变 -> 断路器 -> 出线断路器；另一台主变 -> 隔离开关
MATCH (a:Device {id:'1'}), (b:Device {id:'3'}) MERGE (a)-[:FEEDS]->(b);
MATCH (a:Device {id:'3'}), (b:Device {id:'6'}) MERGE (a)-[:FEEDS]->(b);
MATCH (a:Device {id:'2'}), (b:Device {id:'4'}) MERGE (a)-[:FEEDS]->(b);
```

现状是两条链：`1 → 3 → 6` 和 `2 → 4`。
**5 号（灭绝变压器）目前没有下游关系**——这是数据缺口，不是查询错误，
README 的「已知限制」里如实写了。

## 四、两跳下游（`GraphTools` 用的就是这条）

```cypher
MATCH p=(d:Device {id:'1'})-[:FEEDS*1..2]->(x)
RETURN x.id AS id, x.name AS name, length(p) AS hops
ORDER BY hops
```

真实输出：

```
id, name, hops
"3", "倚天剑断路器", 1
"6", "天鹰刀断路器", 2
```

三个必须记牢的语法细节：

1. **`*1..2` 写在关系类型后面**，`[:FEEDS*1..2]`；
   写成 `[:FEEDS]*1..2` 是语法错误，写成 `-[:FEEDS*1..3]->` 才是三跳。
2. **方向不能反**：`(d)-[:FEEDS]->(x)` 是「d 供 x」，
   `(d)<-[:FEEDS]-(x)` 是「x 供 d」，问下游写反了会得到空集。
3. **`length(p)` 是路径长度**，不是节点数；一跳 = 1，两跳 = 2。

## 五、反向查询（谁给它供电）

`GraphTools` 只实现了下游。要查上游（故障设备由谁供电、停电要通知谁）用这条：

```cypher
MATCH p=(x)-[:FEEDS*1..2]->(d:Device {id:'6'})
RETURN x.id AS id, x.name AS name, length(p) AS hops
ORDER BY hops
```

## 六、排查表

| 现象 | 原因 | 处理 |
|---|---|---|
| 查询返回空，但不报错 | id 传了整数 `1`，节点里存的是字符串 `'1'` | 参数写成 `'1'` |
| `routers=[]` / 服务不可用 | Browser 的 Connect URL 写错 | Neo4j 5 用 `neo4j://localhost:7687`，不要写 http、不要带库名 |
| 两跳只出一条 | 关系只建了一段 | `MATCH (a)-[r:FEEDS]->(b) RETURN a.name,b.name` 核一遍 |
| 建节点重复 | 没用 `MERGE` 或 id 不唯一 | 先建唯一约束，再用 `MERGE` |

## 七、当前库状态（复制即可核对）

```cypher
MATCH (d:Device) RETURN count(d);                    // 期望 6
MATCH ()-[r:FEEDS]->() RETURN count(r);              // 期望 3
```

```bash
# 命令行核对（容器名 neo4j）
docker exec neo4j cypher-shell -u neo4j -p <你的密码> \
  "MATCH (a)-[r:FEEDS]->(b) RETURN a.name, b.name;"
```
