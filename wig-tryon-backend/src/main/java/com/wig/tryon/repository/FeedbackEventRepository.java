package com.wig.tryon.repository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 反馈/行为埋点的内存存储（MVP）。
 * 仅保留最近 N 条用于演示漏斗，避免无限增长。
 */
@Repository
public class FeedbackEventRepository {

    private static final int MAX_EVENTS = 5000;
    private final ConcurrentLinkedDeque<Event> events = new ConcurrentLinkedDeque<>();

    public static class Event {
        public String sessionId;
        public Long productId;
        public String eventType;
        public String sentiment;
        public String channel;
        public Instant ts = Instant.now();
    }

    public void add(Event event) {
        events.addLast(event);
        while (events.size() > MAX_EVENTS) {
            events.pollFirst();
        }
    }

    public Map<String, Object> funnelSummary() {
        List<Event> snapshot = new ArrayList<>(events);
        long upload = count(snapshot, "upload_photo");
        long enterWall = count(snapshot, "enter_wall");
        long addToWall = count(snapshot, "add_to_wall");
        long compare = count(snapshot, "open_compare");
        long consult = count(snapshot, "open_consult");
        long copyWechat = count(snapshot, "consult_copy_wechat");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalEvents", snapshot.size());
        r.put("uploadPhoto", upload);
        r.put("enterWall", enterWall);
        r.put("addToWall", addToWall);
        r.put("openCompare", compare);
        r.put("openConsult", consult);
        r.put("copyWechat", copyWechat);
        r.put("consultRate", ratio(consult, upload));
        r.put("compareRate", ratio(compare, upload));
        return r;
    }

    private long count(List<Event> list, String eventType) {
        return list.stream().filter(e -> eventType.equals(e.eventType)).count();
    }

    private String ratio(long a, long b) {
        if (b <= 0) return "0%";
        return String.format("%.1f%%", a * 100.0 / b);
    }
}

