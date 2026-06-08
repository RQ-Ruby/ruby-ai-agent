# 行旅 AI · Travel AI Agent

基于 **Spring Boot 3.4 + Spring AI + Vue 3** 的旅游垂直领域 AI 超级智能体。

## 项目架构

```
┌────────────────────────────────────────────────┐
│  前端  Vue 3 + TypeScript + Element Plus       │
│  SSE 流式对话 · Markdown 渲染 · chatId 会话管理  │
└──────────────────┬─────────────────────────────┘
                   │ SSE / REST
┌──────────────────▼─────────────────────────────┐
│  后端  Spring Boot 3.4 + Spring AI             │
│                                                │
│  ┌─────────────┐  ┌──────────────────────────┐ │
│  │ TravelApp   │  │ TravelAgent (ReAct Agent)│ │
│  │ 旅游问答    │  │ 自主规划 · 工具调用       │ │
│  └──────┬──────┘  └──────────┬───────────────┘ │
│         │                    │                 │
│  ┌──────▼────────────────────▼───────────────┐ │
│  │          Tool Chain (工具链)               │ │
│  │  WebSearch · WebScraping · TravelPlan     │ │
│  │  BudgetCalculator · PDFGeneration         │ │
│  │  WeatherQuery · AttractionRecommend       │ │
│  │  HotelSearch · FlightSearch               │ │
│  └───────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────┐ │
│  │  RAG 知识库 (PgVector / SimpleVectorStore)│ │
│  │  旅游攻略 Markdown 文档 → 向量检索         │ │
│  └───────────────────────────────────────────┘ │
└────────────────────────────────────────────────┘
```

## 技术栈

| 层级     | 技术                                        |
|--------|-------------------------------------------|
| 前端     | Vue 3 + Vite + Element Plus + Axios       |
| 后端     | Spring Boot 3.4 + Spring AI 1.0.0-M6      |
| 大模型    | 阿里通义千问 (DashScope)                        |
| 向量库    | PgVector (PostgreSQL) / SimpleVectorStore |
| 工具调用   | Spring AI `@Tool` 注解 + MCP 协议             |
| 序列化    | Kryo 5                                    |
| PDF 生成 | iText 9                                   |
| 文档解析   | Jsoup + Spring AI Markdown Reader         |

## 核心功能

### 1. TravelApp — 旅游问答

- 基于 ChatClient + RAG 的旅游知识问答
- 多轮对话记忆 (InMemoryChatMemory)
- 工具调用 + MCP 集成
- SSE 流式输出

### 2. TravelAgent — 规划智能体

- ReAct (Reasoning + Acting) 自主循环
- 自主决策，缺失信息使用合理默认值
- 工具链：搜索 → 行程生成 → 预算计算 → PDF 输出
- 按 chatId 缓存实例，支持多轮会话

### 3. 旅游工具链

| 工具                        | 说明               |
|---------------------------|------------------|
| `WebSearchTool`           | 互联网搜索旅游信息        |
| `WebScrapingTool`         | 抓取网页内容           |
| `TravelPlanTool`          | 生成结构化旅游行程        |
| `BudgetCalculatorTool`    | 旅行预算核算           |
| `PDFGenerationTool`       | 行程手册 PDF 生成 + 下载 |
| `WeatherQueryTool`        | 目的地天气查询          |
| `AttractionRecommendTool` | 景点推荐             |
| `HotelSearchTool`         | 酒店模拟查询           |
| `FlightSearchTool`        | 航班模拟查询           |

### 4. RAG 知识库

- 旅游攻略 Markdown 文档自动加载
- 向量化存储 + 语义检索
- 查询重写优化检索效果

## 快速启动

### 后端

```bash
# 1. 配置 application-local.yml（DashScope API Key、PostgreSQL、SearchAPI Key）
# 2. 启动
mvn spring-boot:run
```

### 前端

```bash
cd ruby-ai-agent-frontend
npm install
npm run dev
```

访问 http://localhost:5173

## 项目结构

```
src/main/java/com/ruby/rubyaiagent/
├── ai/                  # AI 应用层
│   └── TravelApp.java   # 旅游问答应用
├── agent/               # 智能体框架
│   ├── BaseAgent.java    # 基类（状态机 + 流式输出）
│   ├── ReActAgent.java   # ReAct 循环
│   ├── ToolCallAgent.java # 工具调用代理
│   └── TravelAgent.java  # 旅游规划智能体
├── tools/               # 工具链
│   ├── TravelPlanTool.java
│   ├── BudgetCalculatorTool.java
│   ├── WeatherQueryTool.java
│   ├── AttractionRecommendTool.java
│   ├── HotelSearchTool.java
│   ├── FlightSearchTool.java
│   ├── PDFGenerationTool.java
│   ├── WebSearchTool.java
│   └── ...
├── rag/                 # RAG 知识库
├── controller/          # API 控制器
├── config/              # 配置类
└── chatmemory/          # 对话记忆
```

## API 接口

| 方法  | 路径                      | 说明                     |
|-----|-------------------------|------------------------|
| GET | `/ai/travel_app/chat`   | 旅游问答（SSE 流式）           |
| GET | `/ai/travel_manus/chat` | 规划智能体（SSE 流式，需 chatId） |

## License

MIT
