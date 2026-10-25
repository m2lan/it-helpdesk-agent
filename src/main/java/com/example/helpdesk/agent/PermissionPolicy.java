package com.example.helpdesk.agent;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;

/**
 * 三态权限策略：allow / ask / deny。
 *
 * <p>这是整个方案里最值钱的一段配置。企业不敢让 AI 碰生产系统，
 * 根因不是模型不聪明，而是「它可能做了一件没人批准的事」。
 * 把边界写在代码里，比写在 Prompt 里可靠一万倍——
 * Prompt 会被绕过，策略不会。
 *
 * <p>设计原则：
 * <ul>
 *   <li>只读类全部 {@code ALLOW}：查日志、查配置本身没有风险，审批反而让人烦</li>
 *   <li>写操作全部 {@code ASK}：解锁账号、开号、重启队列，都要人点一下</li>
 *   <li>生产高危 {@code DENY}：不是「审批后可以做」，是「谁批都不做」</li>
 * </ul>
 */
public final class PermissionPolicy {

    private static final String SOURCE = "it-helpdesk-policy/v1";

    private PermissionPolicy() {
    }

    public static PermissionContextState itHelpdesk() {
        return PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)

                // ---- 只读：直接放行 ----
                .addAllowRule("query_app_logs", allow("query_app_logs"))
                .addAllowRule("list_recent_deploys", allow("list_recent_deploys"))
                .addAllowRule("search_knowledge_base", allow("search_knowledge_base"))
                .addAllowRule("get_config", allow("get_config"))
                .addAllowRule("query_metrics", allow("query_metrics"))
                .addAllowRule("query_slow_sql", allow("query_slow_sql"))
                .addAllowRule("list_running_jobs", allow("list_running_jobs"))
                .addAllowRule("get_ad_account", allow("get_ad_account"))
                .addAllowRule("query_auth_events", allow("query_auth_events"))
                .addAllowRule("get_printer_status", allow("get_printer_status"))
                .addAllowRule("get_printer_events", allow("get_printer_events"))
                .addAllowRule("get_onboarding", allow("get_onboarding"))
                .addAllowRule("check_account", allow("check_account"))

                // ---- 写操作：必须人工确认 ----
                .addAskRule("unlock_ad_account", ask("unlock_ad_account"))
                .addAskRule("restart_print_queue", ask("restart_print_queue"))
                .addAskRule("create_account", ask("create_account"))
                .addAskRule("create_ticket", ask("create_ticket"))

                // ---- 生产高危：直接拒绝，不进审批队列 ----
                .addDenyRule("restart_service", deny("restart_service"))

                .build();
    }

    private static PermissionRule allow(String tool) {
        return new PermissionRule(tool, "", PermissionBehavior.ALLOW, SOURCE);
    }

    private static PermissionRule ask(String tool) {
        return new PermissionRule(tool, "", PermissionBehavior.ASK, SOURCE);
    }

    private static PermissionRule deny(String tool) {
        return new PermissionRule(tool, "", PermissionBehavior.DENY, SOURCE);
    }
}
