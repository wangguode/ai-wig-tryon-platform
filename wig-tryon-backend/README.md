# 假发试戴 AI 后端服务（wig-tryon-backend）

基于 **Spring Boot 3** 的 MVP 后端，实现 VS-MPV.md 中的核心链路：

```
上传照片 → AI 分析推荐 → 选择发型 → 提交试戴(异步) → 轮询生成 → 效果/反馈 → 门店
```

## 一、设计取舍（为"快速演示、零依赖启动"优化）

| 文档方案 | 本实现 | 说明 |
|---|---|---|
| MySQL | 内存仓库 `CatalogRepository` | 免装数据库，种子数据与前端一致 |
| Redis | 内存会话 `SessionRepository`（含 TTL） | 免装 Redis |
| MinIO/OSS | 本地文件存储 `StorageService`（`/files/**`） | 免装 Docker |
| OpenAI / 阿里百炼 | `AiService` 抽象层 + DashScope 实现 + **Mock 降级** | 无 Key 也能离线跑通 |

> AI 能力做成可插拔接口（`AiService`），与文档"换模型只改实现/配置、业务代码 0 改动"理念一致。
> 默认 `AI_MODE=seed`，**无需任何 API Key 即可启动并跑通整条流程**（种子模式会返回预制效果图，适合稳定演示）。

## 二、快速启动（默认 Mock 模式，零依赖）

要求：JDK 17+、Maven 3.9+

```bash
cd wig-tryon-backend
mvn spring-boot:run
# 启动后监听 http://localhost:8080
```

## 三、启用真实大模型（阿里云百炼 DashScope，中国区直连）

```bash
export DASHSCOPE_API_KEY=sk-你的百炼key
export AI_MODE=real
# 可选：严格真实联调，失败不降级，便于排查问题
export AI_STRICT_REAL=true
mvn spring-boot:run
```

- 视觉分析：`qwen-vl-max`（脸型/发际线/肤色/白发分析 + 推荐）
- 试戴生成：`wanx2.1-imageedit`（异步任务：提交 → 轮询）

> 注意：图像编辑接口一般更偏好**可公网访问**图片 URL。当前实现已支持把本地图片自动转 data URI，
> 便于本机直连调试；线上环境仍建议将 `app.storage.public-base-url` 指向 OSS/CDN 公网地址。

## 四、核心接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/photo/upload` | 上传照片（multipart `file`），返回 `sessionId` |
| GET | `/api/recommend/analyze/{sessionId}?preference=` | AI 分析 + 个性化推荐 |
| GET | `/api/recommend/products?category=&faceType=` | 发型库列表 |
| POST | `/api/tryon/submit` | 提交试戴 `{sessionId,wigProductId,adjustmentNote,tryonType}` |
| GET | `/api/tryon/status/{taskId}` | 轮询任务状态（含 progress） |
| POST | `/api/tryon/adjust?taskId=&adjustmentNote=` | 调整重生成（刘海/发际线等） |
| GET | `/api/store/nearby` · `/api/store/{id}` | 门店列表/详情 |
| POST | `/api/feedback` | 效果反馈 |
| GET | `/api/system/ai-status` | AI 模式与真实联调就绪状态检查 |

## 五、目录结构

```
src/main/java/com/wig/tryon/
├── config/        # 配置：CORS、静态资源、Bean、属性
├── controller/    # REST 控制器
├── service/       # 业务服务
│   └── ai/        # AI 抽象层：AiService / DashScope / Mock / Router / PromptBuilder
├── repository/    # 内存仓库（发型库/门店/会话/任务）
└── model/         # 实体与 DTO
```

## 六、客户需求对应

- 价格区间：`WigProduct.priceMin/priceMax`，展示 `¥3,800 - ¥6,500`
- 按喜好/脸型推荐（非按价格）：`PromptBuilder.analysisSystemPrompt`
- 白发遮盖 / M 型发际线 / 刘海 / 局部调整：`PromptBuilder.tryonPrompt` + `tryonType`
- 真实发丝感（消蜡像）：prompt 质量指令（发丝根根分明、自然光泽、避免塑料/蜡像感）
- 效果不满意可调整：`/api/tryon/adjust`
