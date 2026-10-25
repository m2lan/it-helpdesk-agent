package com.example.helpdesk.agent;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;

/**
 * 离线回放模型：不调用任何大模型，直接返回一段预设答复。
 *
 * <p><b>它验证什么</b>：Spring 装配、工具注册、Agent 构建、HTTP 接口、SSE 推送。
 * clone 下来没有 API Key 也能 curl 通，先确认工程没问题，再去申请 Key。
 *
 * <p><b>它不验证什么</b>：真正的 ReAct 决策与工具执行链。
 * 这里刻意不伪造 tool_use 消息——如果伪造，ReActAgent 会把它们当普通对话内容存进历史，
 * 看起来「跑了 5 轮推理」，实际一个工具都没执行。这种假跑通比跑不起来更危险。
 * 要验证工具调用和权限拦截，必须配 DASHSCOPE_API_KEY 走真模型。
 */
public class ScriptedModel implements Model {

    @Override
    public String getModelName() {
        return "scripted/offline-demo";
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages,
                                     List<ToolSchema> tools,
                                     GenerateOptions options) {
        String userText = lastText(messages);
        String text = (userText.contains("重启") && userText.contains("库"))
                ? REFUSED_TEXT
                : ERP_ANSWER_TEXT;
        return Flux.just(response(text));
    }

    private String lastText(List<Msg> messages) {
        if (messages == null) {
            return "";
        }
        return messages.stream()
                .filter(m -> m.getContent() != null)
                .flatMap(m -> m.getContent().stream())
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).getText())
                .reduce((a, b) -> b)
                .orElse("")
                .toLowerCase(Locale.ROOT);
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder()
                .id("scripted-" + System.nanoTime())
                .content(List.of(TextBlock.builder().text(text).build()))
                .usage(new ChatUsage(320, 260, 0.2))
                .finishReason("stop")
                .build();
    }

    private static final String ERP_ANSWER_TEXT = """
            【结论】erp-order 服务 v2.4.1 的懒加载改动引入了空指针，不是数据问题，也不是环境问题。

            【依据】
            1. 近 30 分钟 3 条 500 全部堆在 OutboundOrderService.java:218，
               异常为 NullPointerException: "dto.address" is null
            2. 该服务今天 08:31 发布 v2.4.1，commit 7c1e9ab「订单地址字段改为懒加载」，与故障起始时间吻合
            3. 知识库 KB-1042 已记录此缺陷，处置方案一致
            4. 配置 erp.order.address.optional 当前为 false，懒加载路径没有空值保护

            【影响范围】近半小时 3 单失败，均为 9 月前创建、未填收货地址的历史订单。

            【建议下一步】
            1. 立即：让提单人补全收货地址后重试，可绕过（已验证逻辑可行）
            2. 2 小时内：回滚 v2.4.1 → v2.4.0，或等 10-27 的 v2.4.2
            3. 长期：fillLogistics 增加 address 空值兜底，并补单测

            【未执行】本次未改动任何生产配置。把配置改成 optional=true 也能缓解，但会放开必填校验，不建议。

            （离线回放模式：这段是预设文本，不包含真实工具调用。配置 DASHSCOPE_API_KEY 后，
             由 qwen-max 实时推理并实际调用 18 个工具。）
            """;

    private static final String REFUSED_TEXT = """
            【已拒绝执行】重启生产数据库命中高危策略，即使走到审批也不会放行。

            【为什么不做】当前实例健康（CPU 41%、连接 12/50、无死锁、无长事务阻塞），
            重启不但解决不了问题，还会造成全站 2-5 分钟不可用。
            这条操作在 PermissionPolicy 里是 DENY，不是「等人批」，是「谁批都不做」。

            【真实原因】一条全表扫描的报表查询（8.72s，缺 idx_supplier_status 索引）
            把整体延迟从 800ms 拉到 4.2s，不是数据库本身的问题。

            【可以马上做的】
            1. 让报表发起人加时间范围重跑（立竿见影）
            2. 提 DBA 变更补索引（根治）
            3. 如果确实需要重启，走变更窗口 + DBA 双人复核，不是现在

            （离线回放模式：策略判定见 PermissionPolicy，规则装配由单元测试覆盖。）
            """;
}
