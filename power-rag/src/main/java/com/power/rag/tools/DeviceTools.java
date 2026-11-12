package com.power.rag.tools;

import com.power.rag.trace.Trace;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DeviceTools {

    /** MySQL 的 power_device / power_alert 库，不是 pgvector 那个 PG */
    private final JdbcTemplate jdbc;

    public DeviceTools(@Qualifier("deviceJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** description 决定模型会不会在正确的时候调它；里面列的字段必须是表里真有的 */
    @Tool(description = "按设备名称或编号查询设备台账，返回设备类型、额定参数（容量/电压）、安装位置、"
            + "生产日期、隐患等级、影响范围，以及所属变电站和该站的运行状态。"
            + "当用户询问某台设备的基本信息、参数、位置、所属变电站时使用。")
    public String queryDevice(
            @ToolParam(description = "设备名称或编号，例如 圣火令主变压器、倚天剑断路器、2") String keyword) {

        // cast(e.id as char)：让「按编号查」共用同一个参数，避免隐式转换告警
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select e.id, e.equipment_name, e.equipment_type, e.rated_params, e.install_location, "
                        + "e.manufacture_date, e.hazard_level, e.impact_scope, e.quantity, "
                        + "s.name as station_name, s.voltage_level, s.run_status "
                        + "from power_equipment e "
                        + "left join substation s on s.id = e.substation_id and s.deleted = 0 "
                        + "where e.deleted = 0 and (e.equipment_name like ? or cast(e.id as char) = ?)",
                "%" + keyword + "%", keyword);

        String result = rows.isEmpty()
                ? "没有找到名称为“" + keyword + "”的设备。"
                : rows.toString();   // 直接返回 Map 列表，够模型读
        Trace.add("queryDevice", "keyword=" + keyword, result);
        return result;
    }

    /** 告警在 power_alert 库，同实例跨库 join；按「未处理优先 + 时间倒序」排，只取最新会全是已处理的 */
    @Tool(description = "查询某台设备的告警记录，返回告警级别、状态（PENDING未处理/ACKNOWLEDGED已确认/RESOLVED已处理）、"
            + "告警描述、发生时间和处理结果，最多 5 条，未处理的排在最前。"
            + "当用户询问设备故障、告警、异常、报警、跳闸记录时使用。")
    public String queryAlarms(
            @ToolParam(description = "设备名称或编号，例如 圣火令主变压器、倚天剑断路器、2") String equipment) {

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select e.equipment_name, a.alert_level, a.alert_status, a.fault_type, "
                        + "a.alert_description, a.occurrence_time, a.resolve_time, a.handle_result "
                        + "from power_alert.alert a "
                        + "join power_equipment e on e.id = a.equipment_id "
                        + "where a.deleted = 0 and (e.equipment_name like ? or cast(e.id as char) = ?) "
                        + "order by case a.alert_status when 'PENDING' then 0 "
                        + "         when 'ACKNOWLEDGED' then 1 else 2 end, a.occurrence_time desc "
                        + "limit 5",
                "%" + equipment + "%", equipment);

        String result = rows.isEmpty()
                ? "没有查到“" + equipment + "”的告警记录。请确认设备名称，或先调用 queryDevice 核对台账。"
                : rows.toString();
        Trace.add("queryAlarms", "equipment=" + equipment, result);
        return result;
    }

    /** 责任人表列名是 name，不是 staff_name */
    @Tool(description = "查询某台设备的历史检修记录，返回故障类型、故障描述、处理结果、检修耗时、"
            + "完成时间和责任人姓名及其在岗状态。"
            + "当用户询问设备维修历史、检修记录、谁负责修、修了多久、之前怎么处理的时使用。")
    public String queryMaintenance(
            @ToolParam(description = "设备名称或编号，例如 圣火令主变压器、倚天剑断路器、2") String equipment) {

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select e.equipment_name, m.fault_type, m.fault_description, m.repair_result, "
                        + "m.repair_hours, m.completion_time, s.name as staff_name, s.work_status "
                        + "from power_alert.maintenance_record m "
                        + "join power_equipment e on e.id = m.equipment_id "
                        + "left join power_alert.maintenance_staff s on s.id = m.staff_id and s.deleted = 0 "
                        + "where m.deleted = 0 and (e.equipment_name like ? or cast(e.id as char) = ?) "
                        + "order by m.occurrence_time desc limit 5",
                "%" + equipment + "%", equipment);

        String result = rows.isEmpty()
                ? "没有查到“" + equipment + "”的检修记录。请确认设备名称，或先调用 queryDevice 核对台账。"
                : rows.toString();
        Trace.add("queryMaintenance", "equipment=" + equipment, result);
        return result;
    }

}
