package com.example.helpdesk.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 写操作工具集：真正会改变系统状态的动作。
 *
 * <p>这些方法不声明 readOnly，权限引擎会把它们路由到 ask 分支，
 * 必须由人点确认才会执行。方法内部仍然要自己做二次校验——
 * 权限引擎拦的是「该不该做」，工具自己拦的是「参数对不对」。
 */
public class WriteTools {

    @Tool(name = "unlock_ad_account",
          description = "解除 AD 域账号锁定。仅当确认是本人多次输错密码导致锁定时使用；"
                      + "不重置密码，用户需用正确密码重连。若认证事件显示跨地域或异常 IP，禁止调用。")
    public String unlockAdAccount(
            @ToolParam(name = "account", description = "域账号") String account,
            @ToolParam(name = "lockDuration", description = "只看最近多久的锁定记录，如 2h") String lockDuration) {

        return "已解锁 " + account + "，账号状态 → ACTIVE。用户需使用正确密码重新连接。";
    }

    @Tool(name = "restart_print_queue",
          description = "清掉打印队列中卡住的任务并恢复队列。会重新打印队列内其余任务，"
                      + "执行前需确认队列里没有敏感文件。")
    public String restartPrintQueue(
            @ToolParam(name = "name", description = "打印机名称") String name,
            @ToolParam(name = "action", description = "动作，如 clear-stuck,resume") String action) {

        return "队列已恢复，" + name + " 的 11 个任务开始打印（卡住的 A5 任务已移出）。";
    }

    @Tool(name = "create_account",
          description = "在指定业务系统创建账号并按岗位模板授权。仅用于已审批入职单的新员工，"
                      + "禁止给现有员工扩权。")
    public String createAccount(
            @ToolParam(name = "system", description = "系统，erp / oa / mail") String system,
            @ToolParam(name = "user", description = "登录名") String user,
            @ToolParam(name = "role", description = "角色或岗位模板") String role) {

        return switch (system) {
            case "erp" -> "ERP 账号已创建：" + user + "，角色「" + role + "」，初始密码已由系统发给主管。";
            case "oa" -> "OA 账号已创建：" + user + "，已加入「渠道运营部」。";
            case "mail" -> "邮箱已开通：" + user + "@corp.com，配额 20GB。";
            default -> "未知系统：" + system;
        };
    }

    @Tool(name = "create_ticket",
          description = "在 ITSM 创建工单并指派。用于把定位结论转成人工跟进的变更/处置任务，"
                      + "本身不改动任何生产系统，属于低风险写入。")
    public String createTicket(
            @ToolParam(name = "system", description = "目标系统，itsm") String system,
            @ToolParam(name = "assignee", description = "指派人，格式 组/姓名") String assignee,
            @ToolParam(name = "priority", description = "优先级 P0-P3") String priority,
            @ToolParam(name = "ref", description = "关联的知识库编号或变更号") String ref) {

        String id = priority.equals("P0") ? "INC-2050" : "CHG-3312";
        return "工单 " + id + " 已创建（" + priority + "），指派给 " + assignee + "，附日志与 " + ref + "。";
    }
}
