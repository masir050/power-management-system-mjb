package com.power.auth.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO
 * <p>接收前端传递的用户名和密码。参数校验在进入 Service 之前完成，
 * 避免把空用户名、超长密码带到 SQL 层。</p>
 */
@Data
public class LoginDTO {
    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50 个字符")
    private String username;

    /** 密码（明文，服务端进行 BCrypt 校验） */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过 64 个字符")
    private String password;
}
