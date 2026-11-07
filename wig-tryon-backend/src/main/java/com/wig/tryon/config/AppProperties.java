package com.wig.tryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用级配置（存储目录、会话时长、AI 模式）。
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Storage storage = new Storage();
    private final Ai ai = new Ai();
    private int sessionTtlHours = 24;

    public Storage getStorage() {
        return storage;
    }

    public Ai getAi() {
        return ai;
    }

    public int getSessionTtlHours() {
        return sessionTtlHours;
    }

    public void setSessionTtlHours(int sessionTtlHours) {
        this.sessionTtlHours = sessionTtlHours;
    }

    public static class Storage {
        private String dir = "./data/storage";
        private String publicBaseUrl = "http://localhost:8080/files";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }
    }

    public static class Ai {
        /** 运行模式：seed（预生成种子图，离线完美演示）/ mock（复用原图）/ real（真实大模型）。 */
        private String mode = "seed";
        /** 兼容旧配置：mock=true 等价于 mode=mock。 */
        private boolean mock = false;
        /** strict=true 时，real 模式下任何真实调用失败都直接报错，不自动降级。 */
        private boolean strictReal = false;
        /**
         * 试戴生成的供应商：dashscope（默认，wan2.1/2.5/2.7 多图融合）/ yunwu（OpenAI 兼容代理，gpt-image / gemini-image 等）。
         * 仅影响 generateTryon 这一步；分析与人脸检测仍走默认链路（dashscope/seed/mock）。
         */
        private String tryonProvider = "dashscope";
        /** 演示模式：推荐不调 VL 模型，按分类固定推 2 款 + 固定文案。 */
        private boolean demoFastRecommend = false;
        /** 演示模式：上传后不调用 detectFace，会话直接 FACE_PASSED。 */
        private boolean demoFastFaceCheck = false;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isMock() {
            return mock;
        }

        public void setMock(boolean mock) {
            this.mock = mock;
        }

        public boolean isStrictReal() {
            return strictReal;
        }

        public void setStrictReal(boolean strictReal) {
            this.strictReal = strictReal;
        }

        public String getTryonProvider() {
            return tryonProvider;
        }

        public void setTryonProvider(String tryonProvider) {
            this.tryonProvider = tryonProvider;
        }

        public boolean isDemoFastRecommend() {
            return demoFastRecommend;
        }

        public void setDemoFastRecommend(boolean demoFastRecommend) {
            this.demoFastRecommend = demoFastRecommend;
        }

        public boolean isDemoFastFaceCheck() {
            return demoFastFaceCheck;
        }

        public void setDemoFastFaceCheck(boolean demoFastFaceCheck) {
            this.demoFastFaceCheck = demoFastFaceCheck;
        }

        /** 解析最终模式（real 需配合有效 Key，在 Router 中再判定）。 */
        public String resolvedMode() {
            if (mock) {
                return "mock";
            }
            return mode == null ? "seed" : mode.toLowerCase();
        }
    }
}
