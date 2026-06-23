package com.wuxx.diagnosis.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * App Instance 新建/更新请求。HTTP 模式需要 ip/arthasHttpPort，
 * TUNNEL 模式只需要 arthasAgentId。
 * 更新时 password 留空表示保留原密文；新建时若配置了认证则需提供 password。
 */
@Data
public class AppInstanceUpsertRequest {

    @NotBlank(message = "appId不能为空")
    private String appId;

    @NotBlank(message = "appName不能为空")
    private String appName;

    @NotBlank(message = "env不能为空")
    private String env;

    private String ip;

    @Min(value = 1, message = "arthasHttpPort无效")
    @Max(value = 65535, message = "arthasHttpPort无效")
    private Integer arthasHttpPort;

    private String arthasUsername;

    /** 留空：新建时表示不设认证；更新时表示保留原密文。 */
    private String arthasPassword;

    private String arthasAgentId;

    /** HTTP / TUNNEL。 */
    private String accessMode;

    /** ONLINE / OFFLINE。 */
    private String status;
}
