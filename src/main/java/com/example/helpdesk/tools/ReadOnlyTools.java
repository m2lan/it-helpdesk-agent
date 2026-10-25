package com.example.helpdesk.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 只读工具集：查日志、查变更、查知识库、查配置、查指标。
 *
 * <p>真实落地时，把每个方法体换成对 ES / CMDB / 运维平台的调用即可，
 * 方法签名和 @Tool 描述保持不变——Agent 的决策质量取决于描述写得准不准，
 * 而不是取决于实现复杂度。
 *
 * <p>readOnly = true 有两个作用：
 * <ol>
 *   <li>权限引擎可以直接放行，不需要人工审批</li>
 *   <li>多个只读工具可以并发执行，缩短整体耗时</li>
 * </ol>
 */
public class ReadOnlyTools {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ReadOnlyTools.class);


    @Tool(name = "query_app_logs", readOnly = true, concurrencySafe = true,
          description = "查询应用服务在指定时间窗口内的日志。当用户报错、页面 500、接口异常时优先调用。"
                      + "keyword 支持类名、接口名、错误关键字的模糊匹配。")
    public String queryAppLogs(
            @ToolParam(name = "service", description = "服务名，如 erp-order、erp-report、cms-api") String service,
            @ToolParam(name = "level", description = "日志级别，可选 ERROR / WARN / INFO") String level,
            @ToolParam(name = "keyword", description = "关键字，如 OutboundOrder、Exception") String keyword,
            @ToolParam(name = "window", description = "时间窗口，如 30m、2h、1d") String window) {

        log.info("[工具执行] query_app_logs service={} keyword={}", service, keyword);
        if (!"erp-order".equals(service)) {
            return """
                    未查询到匹配日志。
                    service=%s  level=%s  keyword=%s  window=%s
                    该服务近 %s 无 %s 级别日志，或服务名有误。""".formatted(service, level, keyword, window, window, level);
        }
        return """
                10:42:17.331 ERROR c.e.o.OutboundOrderService - submit failed, orderId=SO20261025031
                java.lang.NullPointerException: Cannot invoke "Address.getCity()" because "dto.address" is null
                \tat com.example.erp.order.OutboundOrderService.fillLogistics(OutboundOrderService.java:218)
                \tat com.example.erp.order.OutboundOrderService.submit(OutboundOrderService.java:154)

                10:43:02.884 同上（orderId=SO20261025032）
                10:44:51.209 同上（orderId=SO20261025033）

                共 3 条，全部落在 OutboundOrderService.java:218""";
    }

    @Tool(name = "list_recent_deploys", readOnly = true, concurrencySafe = true,
          description = "列出服务最近的发布记录，含版本号、发布时间、commit 与变更摘要。"
                      + "排查『昨天还好今天不行』类问题时必须调用，用于确认是否由变更引起。")
    public String listRecentDeploys(
            @ToolParam(name = "service", description = "服务名") String service,
            @ToolParam(name = "hours", description = "回溯小时数，24 表示最近一天") int hours) {

        if (!"erp-order".equals(service)) {
            return "近 " + hours + " 小时 " + service + " 无发布记录。";
        }
        return """
                v2.4.1   08:31  已部署   commit 7c1e9ab
                  └ 订单地址字段改为懒加载（lazy load）
                  └ 影响 OutboundOrderService / OrderQueryService
                v2.4.0   3 天前  已部署   commit a91b30c""";
    }

    @Tool(name = "search_knowledge_base", readOnly = true, concurrencySafe = true,
          description = "检索 IT 知识库，包含历史故障、已知缺陷、处置预案。"
                      + "定位到问题现象后应调用此工具，避免重复解决已经有人踩过的坑。")
    public String searchKnowledgeBase(
            @ToolParam(name = "query", description = "检索语句，用问题现象描述，不要用工具名") String query,
            @ToolParam(name = "top", description = "返回条数，默认 3") int top) {

        return """
                KB-1042  [v2.4.1 已知缺陷] 出库单提交 NPE        相关度 0.94
                  现象：地址改为懒加载后，未填收货地址的历史订单在提交时抛 NPE
                  缓解：① 引导补全收货地址  ② 临时置 erp.order.address.optional=true
                  根治：升级至 v2.4.2（预计 10-27 发布）或回滚 v2.4.0
                  负责人：ERP 二线 @李工""";
    }

    @Tool(name = "get_config", readOnly = true, concurrencySafe = true,
          description = "读取指定配置项在当前环境的生效值与最后变更信息。给出的处置方案涉及开关时，"
                      + "必须先确认实际值，不要假设默认值。")
    public String getConfig(
            @ToolParam(name = "key", description = "配置项 key，如 erp.order.address.optional") String key,
            @ToolParam(name = "env", description = "环境，prod / staging / dev") String env) {

        return key + " = false\n"
             + "最后变更：2026-09-12  by ops-admin\n"
             + "说明：为 false 时收货地址为必填，懒加载路径缺少空值保护";
    }

    @Tool(name = "query_metrics", readOnly = true, concurrencySafe = true,
          description = "查询服务或中间件的监控指标，用于判断是资源问题还是应用问题。"
                      + "用户说『慢』『卡』『是不是要挂了』时优先调用。")
    public String queryMetrics(
            @ToolParam(name = "service", description = "服务或实例名") String service,
            @ToolParam(name = "metric", description = "指标名，如 http_p99、cpu、conn") String metric,
            @ToolParam(name = "window", description = "时间窗口，如 1h、6h") String window) {

        if (service.contains("mysql")) {
            return """
                    CPU 41%   连接 12/50   慢查询 3 条（最长 8.72s）
                    主从延迟 0.3s   无死锁   无长事务阻塞

                    判定：实例健康，重启无收益且会造成 2-5 分钟全站不可用。""";
        }
        return """
                08:00  812ms
                10:00  890ms
                11:00  1.2s
                11:20  4.21s  ← 拐点
                13:00  4.38s
                14:30  4.02s

                CPU 38%  内存 61%  连接池 12/50  → 资源层面正常，排除宕机前兆""";
    }

    @Tool(name = "query_slow_sql", readOnly = true, concurrencySafe = true,
          description = "查询数据库近期的慢查询，返回执行耗时、执行计划要点与发起来源。"
                      + "用于确认『慢』是否由某条 SQL 引起。")
    public String querySlowSql(
            @ToolParam(name = "window", description = "时间窗口") String window,
            @ToolParam(name = "minDuration", description = "最小耗时，单位毫秒") int minDuration,
            @ToolParam(name = "top", description = "返回条数") int top) {

        return """
                1) 8.72s  SELECT ... FROM purchase_order po LEFT JOIN order_item ... WHERE ...
                   → 全表扫描 purchase_order（缺 idx_supplier_status）
                   来源：report-service / 用户 liuyan / 报表「供应商采购明细」
                2) 3.41s  同类查询（分页第 2 页）
                3) 3.02s  同类查询（分页第 3 页）""";
    }

    @Tool(name = "list_running_jobs", readOnly = true, concurrencySafe = true,
          description = "列出正在运行的批处理、报表导出、定时任务。用于确认是否有大任务在占用资源。")
    public String listRunningJobs(
            @ToolParam(name = "service", description = "服务名，如 report-service") String service) {

        return """
                RUNNING  RPT-2291  供应商采购明细导出
                  发起人：liuyan（采购部）  开始：11:20  进度：68%  预计剩余 22 分钟
                  导出范围：全量 2026 年（约 84 万行）""";
    }

    @Tool(name = "get_ad_account", readOnly = true, concurrencySafe = true,
          description = "查询域账号状态，包括是否锁定、锁定原因、密码是否过期、MFA 绑定情况。"
                      + "用户说登录失败、认证失败时先查这个，不要直接重置密码。")
    public String getAdAccount(
            @ToolParam(name = "account", description = "域账号，如 zhangwei") String account) {

        return """
                账号：CORP\\%s（张伟 / 市场部）
                状态：LOCKED   锁定时间：2026-10-25 14:08   原因：连续 5 次密码错误
                密码过期：否（2026-12-03）
                MFA：已绑定""".formatted(account);
    }

    @Tool(name = "query_auth_events", readOnly = true, concurrencySafe = true,
          description = "查询账号的认证事件流水，含来源 IP 与成败。用于区分『本人输错密码』和『撞库攻击』，"
                      + "这个判断直接决定能不能自助解锁。")
    public String queryAuthEvents(
            @ToolParam(name = "account", description = "域账号") String account,
            @ToolParam(name = "window", description = "时间窗口") String window) {

        return """
                14:02  FAIL  源 IP 113.89.xx.41（深圳）
                14:04  FAIL  源 IP 113.89.xx.41
                14:05  FAIL  源 IP 113.89.xx.41
                14:06  FAIL  源 IP 113.89.xx.41
                14:08  FAIL  源 IP 113.89.xx.41  → 触发锁定策略

                5 次全部同一 IP，无跨地域特征，判定为本人手误而非撞库。""";
    }

    @Tool(name = "get_printer_status", readOnly = true, concurrencySafe = true,
          description = "查询网络打印机的在线状态、耗材与队列情况。")
    public String getPrinterStatus(
            @ToolParam(name = "name", description = "打印机名称，如 FIN-HP-02") String name) {

        return """
                设备：%s（财务室）
                在线：是   耗材：正常
                队列：12 个任务   状态：全部 PAUSED
                队首任务：2026-10-25 09:12  RPT-凭证打印-0925.pdf""".formatted(name);
    }

    @Tool(name = "get_printer_events", readOnly = true, concurrencySafe = true,
          description = "查询打印机事件日志，用于定位队列暂停、卡纸、解析错误的具体原因。")
    public String getPrinterEvents(
            @ToolParam(name = "name", description = "打印机名称") String name,
            @ToolParam(name = "window", description = "时间窗口") String window) {

        return """
                09:12  ERROR  PCL XL error: 纸张尺寸不匹配（任务要求 A5，纸盒 1 为 A4）
                09:12  WARN   队列已暂停，等待人工处理
                09:13-09:40  后续 11 个任务入队，均未处理""";
    }

    @Tool(name = "get_onboarding", readOnly = true, concurrencySafe = true,
          description = "查询新员工入职单，返回岗位、应开通的系统清单与审批状态。开通账号前必须先查，"
                      + "按岗位模板授权，不要凭口头描述开权限。")
    public String getOnboarding(
            @ToolParam(name = "name", description = "员工姓名") String name,
            @ToolParam(name = "date", description = "入职日期，格式 yyyy-MM-dd") String date) {

        return """
                员工：%s（E20261023）
                部门：渠道运营部   岗位：渠道运营专员
                入职日：%s   状态：已入职
                应开通：ERP（销售模块·只读）、OA、企业邮箱
                主管：周敏
                备注：入职单 10-23 已审批，IT 受理单未生成""".formatted(name, date);
    }

    @Tool(name = "check_account", readOnly = true, concurrencySafe = true,
          description = "检查账号在指定系统中是否已存在。开通前先查，避免重复创建。")
    public String checkAccount(
            @ToolParam(name = "user", description = "登录名") String user,
            @ToolParam(name = "systems", description = "系统列表，逗号分隔，如 erp,oa,mail") String systems) {

        return """
                erp   不存在
                oa    不存在
                mail  不存在

                结论：入职单审批通过但未流转到 IT 受理，三个系统均未开号。""";
    }
}
