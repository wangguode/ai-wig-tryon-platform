# 演示快速人脸跳过（方案 A）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task (dispatch subagents, or its sequential fallback if subagents are unavailable). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增独立配置 `app.ai.demo-fast-face-check`（默认 `true`），开启时上传后同步 `FACE_PASSED`、不调 qwen-vl；配合已有 `demo-fast-recommend`，演示链路「上传 → 选发型推荐」全程零 VL。

**Architecture:** 配置进 `AppProperties.Ai`；`PhotoService.uploadAndPreCheck` 在 demo 分支同步写 session 状态并跳过 `photoCheckExecutor`；`/api/system/ai-status` 暴露两 demo 开关供运维/前端观测；前端仅改 UploadPage 误导文案（路径 1：后端 PASSED 后首轮 poll 即结束，不改 SelectHairPage 逻辑）。

**Tech Stack:** Spring Boot 3.3 / Java 17、`spring-boot-starter-test` + Mockito、React 19 + Vite TS

**设计依据:** `docs/demo-fast-upload-design.md` §4 方案 A + §7 选项 A（默认 `true`）

---

## 文件结构（实施前锁定）

| 路径 | 职责 |
|---|---|
| `wig-tryon-backend/src/main/java/com/wig/tryon/config/AppProperties.java` | **改** 新增 `demoFastFaceCheck` 字段 + getter/setter |
| `wig-tryon-backend/src/main/resources/application.yml` | **改** `demo-fast-face-check: ${AI_DEMO_FAST_FACE_CHECK:true}` |
| `wig-tryon-backend/src/main/resources/application-demo-recording.yml` | **改** 显式 `demo-fast-face-check: true` |
| `wig-tryon-backend/src/main/java/com/wig/tryon/service/PhotoService.java` | **改** demo 分支跳过异步 detectFace |
| `wig-tryon-backend/src/main/java/com/wig/tryon/controller/SystemController.java` | **改** ai-status 返回 demo 开关 |
| `wig-tryon-backend/src/test/java/com/wig/tryon/service/PhotoServiceDemoFastFaceCheckTest.java` | **新建** TDD 单元测试 |
| `ai-hairstyle-mini-program-ui/src/pages/UploadPage.tsx` | **改** 文案「上传中…」 |
| `ai-hairstyle-mini-program-ui/src/api/client.ts` | **改** `AiStatus` 增加可选 demo 字段 |
| `docs/demo-fast-upload-design.md` | **改** 状态改为「已实现」 |
| `AiService` / `MockAiService` / `SeedAiService` / `DashScopeAiService` / `PromptBuilder` | **不改** |

---

## 范围边界（对照设计自检）

| 设计条款 | 本计划 Task |
|---|---|
| 独立配置 `demo-fast-face-check` | Task 1 |
| 默认 `true`（演示友好） | Task 1 |
| 上传同步 PASSED、不调 detectFace | Task 2 |
| 关闭时行为不变 | Task 2 测试覆盖 |
| ai-status 暴露开关 | Task 3 |
| UploadPage 文案 | Task 4 |
| SelectHairPage 路径 1（不改 poll） | 无 Task（后端 PASSED 即可） |
| 试戴不受影响 | 不改 TryonService；Task 5 手工回归 |
| demo 角标 UI | **YAGNI 不做**（设计 §6.3 可选） |

---

### Task 1: 配置项 `demoFastFaceCheck`

**Files:**
- Modify: `wig-tryon-backend/src/main/java/com/wig/tryon/config/AppProperties.java`
- Modify: `wig-tryon-backend/src/main/resources/application.yml`
- Modify: `wig-tryon-backend/src/main/resources/application-demo-recording.yml`

- [ ] **Step 1: 修改 AppProperties**

在 `AppProperties.Ai` 类中，`demoFastRecommend` 字段下方新增：

```java
/** 演示模式：上传后不调用 detectFace，会话直接 FACE_PASSED。 */
private boolean demoFastFaceCheck = false;

public boolean isDemoFastFaceCheck() {
    return demoFastFaceCheck;
}

public void setDemoFastFaceCheck(boolean demoFastFaceCheck) {
    this.demoFastFaceCheck = demoFastFaceCheck;
}
```

- [ ] **Step 2: 修改 application.yml**

在 `app.ai.demo-fast-recommend` 下一行追加：

```yaml
    # 演示加速：true 时上传后跳过 qwen-vl 人脸检测，直接 PASSED
    demo-fast-face-check: ${AI_DEMO_FAST_FACE_CHECK:true}
```

- [ ] **Step 3: 修改 application-demo-recording.yml**

```yaml
app:
  ai:
    mode: real
    strict-real: false
    tryon-provider: dashscope
    demo-fast-recommend: true
    demo-fast-face-check: true
```

- [ ] **Step 4: 编译验证**

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
./mvnw -q compile -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add wig-tryon-backend/src/main/java/com/wig/tryon/config/AppProperties.java \
        wig-tryon-backend/src/main/resources/application.yml \
        wig-tryon-backend/src/main/resources/application-demo-recording.yml
git commit -m "feat: add demo-fast-face-check config for demo upload acceleration"
```

---

### Task 2: PhotoService 演示分支（TDD）

**Files:**
- Create: `wig-tryon-backend/src/test/java/com/wig/tryon/service/PhotoServiceDemoFastFaceCheckTest.java`
- Modify: `wig-tryon-backend/src/main/java/com/wig/tryon/service/PhotoService.java`

- [ ] **Step 1: 写失败测试**

`wig-tryon-backend/src/test/java/com/wig/tryon/service/PhotoServiceDemoFastFaceCheckTest.java`:

```java
package com.wig.tryon.service;

import com.wig.tryon.config.AppProperties;
import com.wig.tryon.repository.SessionRepository;
import com.wig.tryon.service.ai.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoServiceDemoFastFaceCheckTest {

    @Mock StorageService storageService;
    @Mock SessionRepository sessionRepository;
    @Mock AiService aiService;
    @Mock TaskExecutor photoCheckExecutor;

    AppProperties appProperties;
    PhotoService photoService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        photoService = new PhotoService(
                storageService, sessionRepository, aiService, photoCheckExecutor, appProperties);
    }

    @Test
    void demoFastFaceCheckTrue_skipsAsyncDetectFace() throws Exception {
        appProperties.getAi().setDemoFastFaceCheck(true);
        MultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Path savedPath = Path.of("/tmp/test-face.jpg");
        when(storageService.save(eq(file), anyString()))
                .thenReturn(new StorageService.Stored("http://localhost/files/x.jpg", savedPath));
        when(sessionRepository.createSession(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    SessionRepository.Session s = new SessionRepository.Session();
                    s.sessionId = inv.getArgument(0);
                    s.faceCheckStatus = PhotoService.FACE_PENDING;
                    return s;
                });

        Map<String, Object> result = photoService.uploadAndPreCheck(file);

        assertEquals(PhotoService.FACE_PASSED, result.get("faceCheckStatus"));
        verify(sessionRepository).updateFaceCheck(anyString(), eq(PhotoService.FACE_PASSED), isNull());
        verify(photoCheckExecutor, never()).execute(any(Runnable.class));
        verify(aiService, never()).detectFace(any(), any());
    }

    @Test
    void demoFastFaceCheckFalse_stillSchedulesAsyncDetectFace() throws Exception {
        appProperties.getAi().setDemoFastFaceCheck(false);
        MultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Path savedPath = Path.of("/tmp/test-face.jpg");
        when(storageService.save(eq(file), anyString()))
                .thenReturn(new StorageService.Stored("http://localhost/files/x.jpg", savedPath));
        when(storageService.readBytes(savedPath)).thenReturn(new byte[]{1, 2, 3});
        when(sessionRepository.createSession(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> new SessionRepository.Session());
        doAnswer(inv -> {
            Runnable r = inv.getArgument(0);
            r.run();
            return null;
        }).when(photoCheckExecutor).execute(any(Runnable.class));
        when(aiService.detectFace(any(), anyString())).thenReturn(true);

        Map<String, Object> result = photoService.uploadAndPreCheck(file);

        assertEquals(PhotoService.FACE_PENDING, result.get("faceCheckStatus"));
        verify(photoCheckExecutor, times(1)).execute(any(Runnable.class));
        verify(aiService, times(1)).detectFace(any(), eq("image/jpeg"));
        verify(sessionRepository).updateFaceCheck(anyString(), eq(PhotoService.FACE_PASSED), isNull());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
./mvnw -q test -Dtest=PhotoServiceDemoFastFaceCheckTest
```

Expected: 编译错误 — `PhotoService` 构造函数参数不匹配（尚未注入 `AppProperties`）

- [ ] **Step 3: 实现 PhotoService**

**3a.** 构造函数增加 `AppProperties appProperties` 参数并赋值字段。

**3b.** 修改 `uploadAndPreCheck` 完整方法体：

```java
public Map<String, Object> uploadAndPreCheck(MultipartFile file) throws IOException {
    String sessionId = UUID.randomUUID().toString().replace("-", "");
    String objectName = "user-photos/" + sessionId + extension(file);
    StorageService.Stored stored = storageService.save(file, objectName);

    sessionRepository.createSession(sessionId, stored.url(), stored.path().toString());

    boolean demoFast = appProperties.getAi().isDemoFastFaceCheck();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sessionId", sessionId);
    result.put("photoUrl", stored.url());

    if (demoFast) {
        sessionRepository.updateFaceCheck(sessionId, FACE_PASSED, null);
        result.put("faceCheckStatus", FACE_PASSED);
        result.put("message", "照片上传成功（演示模式，已跳过人脸检测）");
        log.info("照片上传成功 sessionId={}, url={}, faceCheck=demo-skip", sessionId, stored.url());
        return result;
    }

    byte[] imageBytes = readSafe(stored.path().toFile().toPath());
    String mimeType = file.getContentType();
    photoCheckExecutor.execute(() ->
            runFaceCheckAsync(sessionId, imageBytes, mimeType, objectName));

    result.put("faceCheckStatus", FACE_PENDING);
    result.put("message", "照片上传成功，人脸检测进行中");
    log.info("照片上传成功 sessionId={}, url={}, faceCheck=async", sessionId, stored.url());
    return result;
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
./mvnw -q test -Dtest=PhotoServiceDemoFastFaceCheckTest
```

Expected:

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add wig-tryon-backend/src/main/java/com/wig/tryon/service/PhotoService.java \
        wig-tryon-backend/src/test/java/com/wig/tryon/service/PhotoServiceDemoFastFaceCheckTest.java
git commit -m "feat: skip async face detect when demo-fast-face-check enabled"
```

---

### Task 3: ai-status 暴露 demo 开关

**Files:**
- Modify: `wig-tryon-backend/src/main/java/com/wig/tryon/controller/SystemController.java`

- [ ] **Step 1: 在 aiStatus() 的 Map 构建处追加两行**

在 `r.put("strictReal", ...)` 之后插入：

```java
r.put("demoFastFaceCheck", appProperties.getAi().isDemoFastFaceCheck());
r.put("demoFastRecommend", appProperties.getAi().isDemoFastRecommend());
```

- [ ] **Step 2: 编译 + 手工 curl 验证**

启动后端（需本地可运行环境）：

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--app.ai.demo-fast-face-check=true"
```

另开终端：

```bash
curl -s http://localhost:8080/api/system/ai-status | python3 -m json.tool
```

Expected JSON `data` 段含：

```json
"demoFastFaceCheck": true,
"demoFastRecommend": true
```

且无 API Key 明文。

- [ ] **Step 3: Commit**

```bash
git add wig-tryon-backend/src/main/java/com/wig/tryon/controller/SystemController.java
git commit -m "feat: expose demo-fast flags on ai-status endpoint"
```

---

### Task 4: 前端文案 + AiStatus 类型

**Files:**
- Modify: `ai-hairstyle-mini-program-ui/src/pages/UploadPage.tsx`
- Modify: `ai-hairstyle-mini-program-ui/src/api/client.ts`

- [ ] **Step 1: UploadPage 改文案**

`UploadPage.tsx` 第 270 行：

```tsx
                上传中...
```

（替换 `AI 上传中...`）

- [ ] **Step 2: client.ts 扩展 AiStatus**

```typescript
export interface AiStatus {
  configuredMode: string;
  effectiveMode: 'real' | 'seed' | 'mock';
  strictReal: boolean;
  demoFastFaceCheck?: boolean;
  demoFastRecommend?: boolean;
  dashscopeKeyConfigured: boolean;
  storagePublicBaseUrl: string;
  storageUrlLikelyPublic: boolean;
  readyForRealDemo: boolean;
  suggestion: string;
}
```

- [ ] **Step 3: 前端 lint / 构建**

```bash
cd /Users/wangguo/Desktop/VS/ai-hairstyle-mini-program-ui
npm run build
```

Expected: `built in ...` 无 TS 错误

- [ ] **Step 4: Commit**

```bash
git add ai-hairstyle-mini-program-ui/src/pages/UploadPage.tsx \
        ai-hairstyle-mini-program-ui/src/api/client.ts
git commit -m "fix: upload button copy and AiStatus demo flags type"
```

---

### Task 5: 端到端验证 + 设计文档归档

**Files:**
- Modify: `docs/demo-fast-upload-design.md`

- [ ] **Step 1: 后端全量测试**

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
./mvnw -q test
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: 演示链路手工验证（demo-recording profile）**

```bash
cd /Users/wangguo/Desktop/VS
./scripts/start-demo-recording.sh
```

操作步骤：
1. 小程序上传任意 JPG（≤10MB）
2. 确认按钮显示「上传中…」（非「AI 上传中…」）
3. 进入选发型页：**<2s** 出现推荐卡片（非长时间「人脸检测中…」）
4. 后端日志：应有 `faceCheck=demo-skip`，**不应**出现 `[PhotoCheck]` 或 `detectFace`

- [ ] **Step 3: 关闭开关回归（可选）**

```bash
AI_DEMO_FAST_FACE_CHECK=false ./mvnw -q spring-boot:run
```

上传后 `faceCheckStatus` 应为 `PENDING`，日志出现异步 `[PhotoCheck]`。

- [ ] **Step 4: 更新设计文档状态**

`docs/demo-fast-upload-design.md` 首段改为：

```markdown
> **状态**：已实现（见 `docs/superpowers/plans/2026-06-13-demo-fast-face-check.md`）
```

§7 待确认项追加：**已选 A（默认 true）**。

- [ ] **Step 5: Commit**

```bash
git add docs/demo-fast-upload-design.md
git commit -m "docs: mark demo-fast-upload design as implemented"
```

---

## 三模式行为差异（实现后自检）

| 配置 | 上传后人脸 | analyze | 试戴 |
|---|---|---|---|
| `demo-fast-face-check=true` | 同步 PASSED，零 VL | 依 `demo-fast-recommend` | 不变（仍走 AI_MODE） |
| `demo-fast-face-check=false` | 异步 qwen-vl | 依 `demo-fast-recommend` | 不变 |
| 两者都 `true` | 零 VL | 固定 2 款 + 文案 | 不变 |

---

## Self-Review（计划 vs 设计）

| 检查项 | 结果 |
|---|---|
| 方案 A 独立开关 | Task 1 ✓ |
| 默认 true | Task 1 ✓ |
| PhotoService 分支 | Task 2 ✓ |
| ai-status | Task 3 ✓ |
| UploadPage 文案 | Task 4 ✓ |
| 不改 AiService 三实现 | 文件结构已排除 ✓ |
| TDD（失败→实现→通过） | Task 2 ✓ |
| 无占位符 | 已含完整代码块 ✓ |
| demo 角标 UI | 明确 YAGNI 跳过 ✓ |

---

## 执行入口

计划已保存。下一步请进入 **`/code`** 按 Task 1→5 顺序实现。

可选执行方式：
1. **Inline** — 本会话连续执行 Task 1~5
2. **Subagent** — 每 Task 派发子 agent，Task 间人工 review
