package com.wig.tryon.service;

import com.wig.tryon.model.BatchJob;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存级批量任务注册表。所有"长任务"（本体批量重画 / Showcase 批量填充等）启动时
 * 在这里登记，前端轮询 dashboard 接口拿状态。
 * <p>
 * 不持久化：进程重启会丢失任务历史。MVP 阶段够用；上生产前可改用 Redis 或落 JSON。
 * 自动清理：保留最近 24h 内的任务避免内存膨胀。
 */
@Service
public class BatchJobRegistry {

    private final ConcurrentHashMap<String, BatchJob> jobs = new ConcurrentHashMap<>();

    public BatchJob register(BatchJob job) {
        evictExpired();
        jobs.put(job.jobId, job);
        return job;
    }

    public Optional<BatchJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /** 当前进行中的同类型任务（用于"重复点击防抖"）。 */
    public Optional<BatchJob> findRunning(BatchJob.Type type) {
        return jobs.values().stream()
                .filter(j -> j.type == type && j.status == BatchJob.Status.RUNNING)
                .findFirst();
    }

    public List<BatchJob> recent(int limit) {
        evictExpired();
        List<BatchJob> list = new ArrayList<>(jobs.values());
        list.sort(Comparator.comparing((BatchJob j) -> j.startedAt).reversed());
        return list.size() <= limit ? list : list.subList(0, limit);
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        jobs.values().removeIf(j -> j.finishedAt != null && j.finishedAt.isBefore(cutoff));
    }
}
