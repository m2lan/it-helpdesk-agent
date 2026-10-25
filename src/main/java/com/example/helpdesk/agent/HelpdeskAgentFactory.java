package com.example.helpdesk.agent;

import com.example.helpdesk.tools.DangerousTools;
import com.example.helpdesk.tools.ReadOnlyTools;
import com.example.helpdesk.tools.WriteTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 组装 Agent：模型 + 工具集 + 权限策略 + 迭代上限。
 *
 * <p>没有配置 API Key 时自动降级为 {@link ScriptedModel}，
 * 保证 clone 下来就能启动，先看清链路再谈接模型。
 */
@Component
public class HelpdeskAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(HelpdeskAgentFactory.class);

    private final String apiKey;
    private final String modelName;
    private final int maxIters;

    public HelpdeskAgentFactory(
            @Value("${helpdesk.api-key:}") String apiKey,
            @Value("${helpdesk.model:qwen-max}") String modelName,
            @Value("${helpdesk.max-iters:12}") int maxIters) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.maxIters = maxIters;
    }

    /**
     * 每次请求创建一个 Agent 实例。
     *
     * <p>Agent 本身很轻（就是配置对象），重的是模型调用。
     * 会话连续性靠 RuntimeContext.sessionId + stateStore 保证，不靠复用实例。
     * 这样即使服务发布重启，同一个 sessionId 也能接着跑——这是
     * 一线场景里必须的能力：用户不会因为你们发版就把问题重说一遍。
     */
    public ReActAgent create() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ReadOnlyTools());
        toolkit.registerTool(new WriteTools());
        toolkit.registerTool(new DangerousTools());

        ReActAgent agent = ReActAgent.builder()
                .name("it-helpdesk")
                .description("企业 IT 服务台一线处置助手")
                .sysPrompt(SystemPrompts.IT_HELPDESK)
                .model(resolveModel())
                .toolkit(toolkit)
                .permissionContext(PermissionPolicy.itHelpdesk())
                .maxIters(maxIters)
                .build();

        log.info("Agent 已就绪：model={} tools={} maxIters={}",
                resolveModelName(), toolkit.getToolNames().size(), maxIters);
        return agent;
    }

    private Model resolveModel() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("未配置 helpdesk.api-key，降级为离线剧本模式（不会真正推理）");
            return new ScriptedModel();
        }
        try {
            // 手动构建而不是依赖 starter 的自动装配：
            // 自动装配在没 key 时会直接让应用启动失败，而一线场景需要「能起来看链路」。
            return DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .stream(true)
                    .build();
        } catch (Exception e) {
            log.warn("DashScope 模型构建失败，降级为离线剧本模式：{}", e.getMessage());
            return new ScriptedModel();
        }
    }

    private String resolveModelName() {
        return (apiKey == null || apiKey.isBlank()) ? "scripted/offline-demo" : "dashscope:" + modelName;
    }
}
