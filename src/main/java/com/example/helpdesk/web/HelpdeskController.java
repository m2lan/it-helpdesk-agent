package com.example.helpdesk.web;

import com.example.helpdesk.agent.HelpdeskAgentFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对外接口。两个入口：
 * <ul>
 *   <li>{@code POST /api/helpdesk/ask}    同步返回，适合 curl 和后台任务</li>
 *   <li>{@code POST /api/helpdesk/stream} SSE，前端可以逐步看到进展</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/helpdesk")
public class HelpdeskController {

    private static final Logger log = LoggerFactory.getLogger(HelpdeskController.class);

    private final HelpdeskAgentFactory factory;
    private final ExecutorService pool = Executors.newFixedThreadPool(16);

    public HelpdeskController(HelpdeskAgentFactory factory) {
        this.factory = factory;
    }

    public record ChatRequest(String message, String userId, String sessionId) {
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody ChatRequest req) {
        long t0 = System.currentTimeMillis();
        String sessionId = req.sessionId() == null || req.sessionId().isBlank()
                ? "sess-" + UUID.randomUUID().toString().substring(0, 8)
                : req.sessionId();

        ReActAgent agent = factory.create();
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(req.userId() == null ? "anonymous" : req.userId())
                .build();

        Msg reply = agent.call(req.message(), ctx).block(Duration.ofSeconds(120));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("answer", reply == null ? "" : reply.getTextContent());
        body.put("elapsedMs", System.currentTimeMillis() - t0);
        return body;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(180_000L);
        String sessionId = req.sessionId() == null || req.sessionId().isBlank()
                ? "sess-" + UUID.randomUUID().toString().substring(0, 8)
                : req.sessionId();

        pool.submit(() -> {
            long t0 = System.currentTimeMillis();
            try {
                emitter.send(SseEmitter.event().name("start")
                        .data(Map.of("sessionId", sessionId, "message", req.message())));

                ReActAgent agent = factory.create();
                RuntimeContext ctx = RuntimeContext.builder()
                        .sessionId(sessionId)
                        .userId(req.userId() == null ? "anonymous" : req.userId())
                        .build();

                Msg reply = agent.call(req.message(), ctx).block(Duration.ofSeconds(120));

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("text", reply == null ? "" : reply.getTextContent());
                payload.put("elapsedMs", System.currentTimeMillis() - t0);
                if (reply != null && reply.getUsage() != null) {
                    payload.put("tokens", reply.getUsage().getTotalTokens());
                }
                emitter.send(SseEmitter.event().name("answer").data(payload));
                emitter.complete();
            } catch (Exception e) {
                log.error("处理失败 sessionId={}", sessionId, e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    // 客户端可能已经断开
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
