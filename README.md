# IT 一线自助 Agent

用 AgentScope-Java 2.0 落地「IT 服务台一线自助」的最小可用骨架。
员工在 IM 里甩一句话，Agent 自己查日志、查变更、查知识库、查账号状态，
能自助解决的当场解决，要动生产系统的先找人审批。

## 先看效果

直接双击打开 `demo/index.html`（纯静态，无需服务）。

左侧 6 条工单覆盖了完整的能力面：

| 工单 | 演示什么 |
|---|---|
| INC-2041 ERP 出库单 500 | 4 次只读工具串联，靠日志 + 变更 + 知识库定位根因 |
| INC-2042 VPN 连不上 | 先用认证流水判断「本人输错」还是「撞库」，再决定能不能自助解锁 |
| INC-2043 打印机打不出 | 从设备事件日志找到队列暂停原因 |
| INC-2044 ERP 白天变慢 | 定位到一条全表扫描的大报表，并主动规避业务影响 |
| INC-2045 新员工没开号 | 三个系统逐个开号，三次审批 |
| INC-2046 要求重启生产库 | 命中 DENY 策略直接拒绝，然后给替代方案 |

重点看三个地方：**工具调用卡片**（Agent 自己决定查什么）、
**黄色审批卡**（写操作必须人点）、**右侧权限策略**（deny 不进审批队列）。

## 跑 Java 版

```bash
cd it-helpdesk-agent

# 1. 离线模式：不需要任何 Key，验证装配和接口通不通
mvn spring-boot:run

# 2. 真模型：换成 qwen-max 实时推理 + 真实工具调用
export DASHSCOPE_API_KEY=sk-xxxx
mvn spring-boot:run

# 3. 跑单元测试（验证 18 个工具的权限规则没漏配）
mvn test
```

> 端口默认 8080。如果被占用，`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`

调用：

```bash
curl -X POST http://localhost:8080/api/helpdesk/ask \
  -H 'Content-Type: application/json' \
  -d '{"message":"ERP 提交出库单一直报 500，客户在等发货","userId":"zhangwei"}'
```

返回：

```json
{
  "sessionId": "sess-7f3a91",
  "answer": "【结论】erp-order 服务 v2.4.1 的懒加载改动引入了空指针……",
  "elapsedMs": 18450
}
```

要流式给前端看进展，用 `POST /api/helpdesk/stream`（SSE，事件名 `start` / `answer` / `error`）。

## 目录

```
it-helpdesk-agent/
├── demo/index.html                       可点演示，先看这个
├── pom.xml
└── src/main/
    ├── resources/application.yml          模型与迭代上限配置
    └── java/com/example/helpdesk/
        ├── agent/
        │   ├── SystemPrompts.java         提示词：把处置 SOP 写成硬约束
        │   ├── PermissionPolicy.java      三态权限：allow / ask / deny
        │   ├── HelpdeskAgentFactory.java  组装 Agent，无 Key 自动降级
        │   └── ScriptedModel.java         离线剧本模型
        ├── tools/
        │   ├── ReadOnlyTools.java         13 个只读工具（放行）
        │   ├── WriteTools.java            4 个写操作（审批）
        │   └── DangerousTools.java        生产高危（拒绝）
        └── web/HelpdeskController.java    /ask 与 /stream
```

## 换成你自己的系统

每个工具方法体都是模拟数据，换掉就行，**方法签名和 `@Tool` 描述不要动**——
Agent 选不选这个工具，全靠 description 写得准不准。

```java
@Tool(name = "query_app_logs", readOnly = true,
      description = "查询应用服务在指定时间窗口内的日志。用户报错、页面 500 时优先调用。")
public String queryAppLogs(
        @ToolParam(name = "service", description = "服务名，如 erp-order") String service,
        @ToolParam(name = "window", description = "如 30m、2h") String window) {
    return esClient.search(service, window);   // ← 只改这里
}
```

## 权限策略怎么调

改 `PermissionPolicy.java` 一处即可。三条经验：

1. **只读一律 allow**。查日志还弹审批，一线会直接弃用
2. **写操作一律 ask**。审批卡里一定写清「这一步会影响什么」，否则没人敢点
3. **DENY 要少而硬**。只放真正不可逆的（重启生产、删库、改权限矩阵）。
   而且被拒绝后，提示词要求 Agent 必须给替代方案，不能只回「我不能做」

## 上线前必须补的

- [ ] 工具换成真实调用，加超时与重试（工具慢会拖死整轮 ReAct）
- [ ] `stateStore` 换成 Redis，否则发布重启丢会话
- [ ] 审批动作接真实 IM（钉钉/飞书卡片回调），不要做成页面弹窗
- [ ] 脱敏：日志进模型前过滤手机号、身份证、密钥
- [ ] 加 OpenTelemetry，每次运行一个 traceId，能回答「它为什么这么做」
- [ ] 灰度：先只开只读类工单，跑两周看准确率再放写操作

## 已知限制

- **离线回放模式不会真的调工具**：它只返回一段预设答复，用来验证装配和接口。
  这不是偷懒——早期版本我伪造过 `tool_use` 消息想"跑通"链路，结果 ReActAgent 把它们
  当普通对话存进历史，看起来跑了 5 轮推理，实际一个工具都没执行。
  这种假跑通比跑不起来更危险，所以删掉了。**要验证工具调用与权限拦截，必须配 Key 用真模型。**
- 演示页的工具返回是前端写死的预置数据，和后端不是同一份数据源（后端才是可落地的部分）
- 审批流目前只有拦截，没有"批准后重放"的持久化队列，生产需要补
- 工具全部是同步阻塞实现，真实系统调用要加超时，否则一个慢工具会拖死整轮 ReAct
