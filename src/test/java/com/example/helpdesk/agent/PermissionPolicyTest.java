package com.example.helpdesk.agent;

import io.agentscope.core.permission.PermissionContextState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限策略装配测试。
 *
 * <p>这个测试保证的是「规则表本身是对的」——哪个工具放行、哪个要审批、哪个拒绝，
 * 一眼能看出有没有漏配。做不到的是「证明框架真的按规则执行了」，
 * 那个必须配真实模型跑集成测试。
 *
 * <p>但漏配是实际项目里最常见的事故：新加了一个写工具忘记登记，
 * 它就悄悄走了默认分支。所以这层测试不能省。
 */
class PermissionPolicyTest {

    private final PermissionContextState ctx = PermissionPolicy.itHelpdesk();

    @Test
    @DisplayName("只读工具全部放行，不打扰一线")
    void readOnlyToolsAreAllowed() {
        Set<String> readOnly = Set.of(
                "query_app_logs", "list_recent_deploys", "search_knowledge_base", "get_config",
                "query_metrics", "query_slow_sql", "list_running_jobs", "get_ad_account",
                "query_auth_events", "get_printer_status", "get_printer_events",
                "get_onboarding", "check_account");

        assertTrue(ctx.getAllowRules().keySet().containsAll(readOnly),
                "以下只读工具没有配 allow，会被默认策略拦住：" + readOnly.stream()
                        .filter(t -> !ctx.getAllowRules().containsKey(t)).toList());
    }

    @Test
    @DisplayName("写操作全部需要人工审批")
    void writeToolsRequireApproval() {
        Set<String> writes = Set.of(
                "unlock_ad_account", "restart_print_queue", "create_account", "create_ticket");

        assertTrue(ctx.getAskRules().keySet().containsAll(writes),
                "以下写操作没有配 ask，可能直接改生产：" + writes.stream()
                        .filter(t -> !ctx.getAskRules().containsKey(t)).toList());
    }

    @Test
    @DisplayName("生产高危一律拒绝，且不进审批队列")
    void destructiveToolsAreDenied() {
        assertTrue(ctx.getDenyRules().containsKey("restart_service"),
                "restart_service 必须配 deny");
        assertFalse(ctx.getAskRules().containsKey("restart_service"),
                "高危操作不能配 ask——那就变成「等人批就能做」了");
    }

    @Test
    @DisplayName("同一个工具不能同时出现在两张规则表里")
    void rulesAreNotConflicting() {
        for (String tool : ctx.getAllowRules().keySet()) {
            assertFalse(ctx.getDenyRules().containsKey(tool), tool + " 同时配了 allow 和 deny");
            assertFalse(ctx.getAskRules().containsKey(tool), tool + " 同时配了 allow 和 ask");
        }
        for (String tool : ctx.getAskRules().keySet()) {
            assertFalse(ctx.getDenyRules().containsKey(tool), tool + " 同时配了 ask 和 deny");
        }
    }
}
