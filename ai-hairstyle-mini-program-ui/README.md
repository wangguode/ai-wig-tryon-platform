# 假发试戴 AI 前端（ai-hairstyle-mini-program-ui）

React 19 + Vite + Tailwind 实现的移动端原型，已接入 `wig-tryon-backend` 真实后端：

```
上传照片 → AI 分析推荐 → 选择发型 → 提交试戴 → 轮询生成 → 效果展示/反馈
```

后端不可用时，关键页面会**自动降级为离线演示流程**，保证随时可展示。

## 启动

```bash
npm install
npm run dev      # http://localhost:5173
```

开发期 `vite.config.ts` 已把 `/api` 与 `/files` 代理到 `http://localhost:8080`（后端服务）。
请先启动后端（见 `../wig-tryon-backend/README.md`）。

生产环境可用环境变量指定后端地址：

```bash
VITE_API_BASE=https://your-backend.example.com npm run build
```

## 与后端联动的关键改动

| 文件 | 作用 |
|---|---|
| `src/api/client.ts` | API 客户端（上传/分析/推荐/试戴/轮询/反馈）+ 类型 |
| `src/pages/UploadPage.tsx` | 真正上传照片、人脸预检，拿到 `sessionId` |
| `src/pages/SelectHairPage.tsx` | 拉取后端发型库；展示 AI 分析与个性化推荐 |
| `src/pages/GeneratingPage.tsx` | 提交试戴任务并轮询进度 |
| `src/pages/ResultPage.tsx` | 展示真实生成结果图、上报反馈 |
| `vite.config.ts` | `/api`、`/files` 反向代理 |

## 说明

- 默认后端为 Mock 模式：试戴"效果图"会复用上传原图（仅演示流程）。配置 DashScope Key 并设 `AI_MOCK=false` 后即为真实生成效果。
