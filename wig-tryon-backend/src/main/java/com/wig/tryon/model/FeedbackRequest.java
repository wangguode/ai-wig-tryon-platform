package com.wig.tryon.model;

/**
 * 试戴效果反馈。
 */
public class FeedbackRequest {

    public String taskId;
    public String sentiment;   // like / neutral / dislike
    public Integer rating;     // 1-5 星
    public String sessionId;
    public Long productId;
    public String eventType;   // 收藏/对比/咨询等行为埋点
    public String channel;     // wechat / phone / store
    public String note;        // 附加信息

    public String getTaskId() {
        return taskId;
    }

    public String getSentiment() {
        return sentiment;
    }

    public Integer getRating() {
        return rating;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getChannel() {
        return channel;
    }

    public String getNote() {
        return note;
    }
}
