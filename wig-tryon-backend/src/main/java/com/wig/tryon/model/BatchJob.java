package com.wig.tryon.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用批量任务跟踪：用于"批量重画本体图""批量生成 showcase""批量生成模特图"等长任务。
 * <p>
 * 字段都用 atomic / volatile 避免轮询线程读到半成品状态。前端轮询 dashboard 接口
 * 显示进度卡，运营点开后能看到每条 item 的成败原因（reason 字段）。
 */
public class BatchJob {

    public enum Type {
        REGENERATE_BARE_WIG,
        FILL_SHOWCASES,
        REGENERATE_MODEL_PORTRAIT
    }

    public enum Status { RUNNING, DONE, CANCELLED }

    public final String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    public final Type type;
    public final Instant startedAt = Instant.now();
    public volatile Instant finishedAt;
    public volatile Status status = Status.RUNNING;
    public final int total;
    public final AtomicInteger completed = new AtomicInteger(0);
    public final AtomicInteger failed = new AtomicInteger(0);
    public final List<Item> items = new ArrayList<>(); // 启动后只追加，不再修改
    /** 用户友好的描述。 */
    public final String description;

    public BatchJob(Type type, int total, String description) {
        this.type = type;
        this.total = total;
        this.description = description;
    }

    /** 单条进度记录。每个 entityId（wigAsset/modelAsset/showcase 的 id）一条。 */
    public static class Item {
        public final long entityId;
        public final String entityName;
        public volatile String state = "PENDING"; // PENDING / RUNNING / DONE / FAILED
        public volatile String reason;            // 失败时填错误信息
        public volatile String resultUrl;         // 成功时填生成的图片 URL
        public volatile Instant updatedAt = Instant.now();

        public Item(long entityId, String entityName) {
            this.entityId = entityId;
            this.entityName = entityName;
        }
    }
}
