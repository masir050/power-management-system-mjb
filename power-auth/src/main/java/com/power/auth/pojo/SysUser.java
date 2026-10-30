package com.power.auth.pojo;

import com.power.common.pojo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 系统用户实体，映射 sys_user 表
 * <p>存储管理后台登录用户的账号信息，密码使用 BCrypt 加密存储。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SysUser extends BaseEntity {
    /** 登录用户名 */
    private String username;
    /** BCrypt 加密后的密码 */
    private String password;
    /** 显示昵称 */
    private String nickname;
    /** 电子邮箱 */
    private String email;
    /** 手机号码 */
    private String phone;
    /** 状态：1-启用，0-禁用 */
    private Integer status;
    /** 角色：ADMIN 总负责人 / OPERATOR 运维人员 / CUSTOMER 用电客户 */
    private String role;
    /** 用电客户ID（role=CUSTOMER 时必填，指向 power_billing 库 electricity_customer） */
    private Long customerId;
    /** 运维人员ID（role=OPERATOR 时必填，指向 power_alert 库 maintenance_staff） */
    private Long staffId;
}
