-- 将 maintenance_staff 表所有人员名字改为倚天屠龙记角色名
-- 按 id 顺序依次分配，确保不重复
-- 要求 MySQL 8.0+ (支持 CTE)
-- 执行方式: 在 MySQL 中 source 此文件，或复制粘贴执行

WITH RECURSIVE name_list (rn, name) AS (
    SELECT 1, '谢逊' UNION ALL
    SELECT 2, '杨逍' UNION ALL
    SELECT 3, '韦一笑' UNION ALL
    SELECT 4, '小昭' UNION ALL
    SELECT 5, '殷离' UNION ALL
    SELECT 6, '杨不悔' UNION ALL
    SELECT 7, '范遥' UNION ALL
    SELECT 8, '黛绮丝' UNION ALL
    SELECT 9, '灭绝师太' UNION ALL
    SELECT 10, '宋远桥' UNION ALL
    SELECT 11, '俞莲舟' UNION ALL
    SELECT 12, '俞岱岩' UNION ALL
    SELECT 13, '张松溪' UNION ALL
    SELECT 14, '张翠山' UNION ALL
    SELECT 15, '殷梨亭' UNION ALL
    SELECT 16, '莫声谷' UNION ALL
    SELECT 17, '空见' UNION ALL
    SELECT 18, '空闻' UNION ALL
    SELECT 19, '空智' UNION ALL
    SELECT 20, '空性' UNION ALL
    SELECT 21, '成昆' UNION ALL
    SELECT 22, '陈友谅' UNION ALL
    SELECT 23, '朱元璋' UNION ALL
    SELECT 24, '常遇春' UNION ALL
    SELECT 25, '徐达' UNION ALL
    SELECT 26, '胡青牛' UNION ALL
    SELECT 27, '王难姑' UNION ALL
    SELECT 28, '朱九真' UNION ALL
    SELECT 29, '卫璧' UNION ALL
    SELECT 30, '武青婴' UNION ALL
    SELECT 31, '何太冲' UNION ALL
    SELECT 32, '班淑娴' UNION ALL
    SELECT 33, '鲜于通' UNION ALL
    SELECT 34, '唐文亮' UNION ALL
    SELECT 35, '宗维侠' UNION ALL
    SELECT 36, '宋青书' UNION ALL
    SELECT 37, '鹿杖客' UNION ALL
    SELECT 38, '鹤笔翁' UNION ALL
    SELECT 39, '司徒千钟' UNION ALL
    SELECT 40, '说不得' UNION ALL
    SELECT 41, '冷谦' UNION ALL
    SELECT 42, '周颠' UNION ALL
    SELECT 43, '彭莹玉' UNION ALL
    SELECT 44, '铁冠道人' UNION ALL
    SELECT 45, '殷野王' UNION ALL
    SELECT 46, '李天垣' UNION ALL
    SELECT 47, '阳顶天' UNION ALL
    SELECT 48, '方东白' UNION ALL
    SELECT 49, '静玄' UNION ALL
    SELECT 50, '贝锦仪' UNION ALL
    SELECT 51, '丁敏君' UNION ALL
    SELECT 52, '苏梦清' UNION ALL
    SELECT 53, '静虚' UNION ALL
    SELECT 54, '静慧' UNION ALL
    SELECT 55, '韩姬'
),
ranked_staff AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM maintenance_staff
)
UPDATE maintenance_staff s
JOIN ranked_staff rs ON s.id = rs.id
JOIN name_list nl ON rs.rn = nl.rn
SET s.name = nl.name;

-- 查看更新结果
SELECT id, name, department, position, work_status FROM maintenance_staff ORDER BY id;
