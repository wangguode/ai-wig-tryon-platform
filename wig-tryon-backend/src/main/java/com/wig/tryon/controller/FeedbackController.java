package com.wig.tryon.controller;

import com.wig.tryon.model.ApiResponse;
import com.wig.tryon.model.FeedbackRequest;
import com.wig.tryon.repository.FeedbackEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 效果反馈（评分/喜好）。MVP 直接记录日志，异步不阻塞用户。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);
    private final FeedbackEventRepository feedbackEventRepository;

    public FeedbackController(FeedbackEventRepository feedbackEventRepository) {
        this.feedbackEventRepository = feedbackEventRepository;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> submit(@RequestBody FeedbackRequest request) {
        log.info(
                "收到反馈/事件 taskId={}, sessionId={}, productId={}, sentiment={}, rating={}, eventType={}, channel={}, note={}",
                request.getTaskId(),
                request.getSessionId(),
                request.getProductId(),
                request.getSentiment(),
                request.getRating(),
                request.getEventType(),
                request.getChannel(),
                request.getNote()
        );

        FeedbackEventRepository.Event event = new FeedbackEventRepository.Event();
        event.sessionId = request.getSessionId();
        event.productId = request.getProductId();
        event.eventType = request.getEventType() == null ? "feedback" : request.getEventType();
        event.sentiment = request.getSentiment();
        event.channel = request.getChannel();
        feedbackEventRepository.add(event);

        return ApiResponse.success(Map.of("received", true));
    }

    @GetMapping("/funnel")
    public ApiResponse<Map<String, Object>> funnel() {
        return ApiResponse.success(feedbackEventRepository.funnelSummary());
    }
}
