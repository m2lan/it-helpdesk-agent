package com.example.helpdesk.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 高危工具集：注册给模型看，但权限引擎对 prod 环境一律 DENY。
 *
 * <p>这里有一个容易被忽略的设计点：工具**要**注册进来，别藏起来。
 * 藏起来模型只会绕远路乱试；注册了、但被策略明确拒绝，模型才能
 * 拿到「这条路不通」的确定反馈，转而给用户替代方案。
 *
 * <p>方法体里再抛一次异常，是防呆：万一权限配置被误改成 allow，
 * 执行层仍然不会真的动生产。
 */
public class DangerousTools {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DangerousTools.class);

    @Tool(name = "restart_service",
          description = "重启服务实例。可用于测试环境的故障恢复。生产环境属于高危操作，"
                      + "会被策略拒绝，此时应改为提供根因分析与替代方案。")
    public String restartService(
            @ToolParam(name = "service", description = "服务或实例名") String service,
            @ToolParam(name = "env", description = "环境，prod / staging / dev") String env,
            @ToolParam(name = "force", description = "是否强制，跳过优雅停机") boolean force) {

        // 这行日志是安全告警：只要出现，说明策略没拦住，应立刻排查权限配置
        log.warn("!!! 高危工具 restart_service 被执行 service={} env={} force={} —— 策略失守",
                service, env, force);

        if ("prod".equalsIgnoreCase(env)) {
            throw new IllegalStateException(
                "生产环境禁止调用 restart_service。请走变更流程（CHG）+ DBA 双人复核。");
        }
        return service + "（" + env + "）已重启，耗时 12s。";
    }
}
