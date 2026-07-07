package com.blakube.bktops.plugin.notification;

import org.jetbrains.annotations.Nullable;







public final class EventContext {

    private final String player;
    private final String position;
    private final String oldPosition;
    private final String topId;
    private final String topName;
    private final String score;
    private final String oldScore;
    private final String rewardCount;

    private EventContext(String player, String position, String oldPosition,
                         String topId, String topName, String score, String oldScore,
                         String rewardCount) {
        this.player      = nvl(player);
        this.position    = nvl(position);
        this.oldPosition = nvl(oldPosition);
        this.topId       = nvl(topId);
        this.topName     = nvl(topName);
        this.score       = nvl(score);
        this.oldScore    = nvl(oldScore);
        this.rewardCount = nvl(rewardCount);
    }

    
    public static EventContext positionUpdate(@Nullable String player, @Nullable String position,
                                              @Nullable String oldPosition, @Nullable String topId,
                                              @Nullable String topName, @Nullable String score,
                                              @Nullable String oldScore) {
        return new EventContext(player, position, oldPosition, topId, topName, score, oldScore, null);
    }

    
    public static EventContext timedReset(@Nullable String topId, @Nullable String topName) {
        return new EventContext(null, null, null, topId, topName, null, null, null);
    }

    
    public static EventContext rewardDelivered(@Nullable String player, @Nullable String position,
                                               @Nullable String topId, @Nullable String topName,
                                               @Nullable String score) {
        return new EventContext(player, position, null, topId, topName, score, null, null);
    }

    public String getPlayer()      { return player; }
    public String getPosition()    { return position; }
    public String getOldPosition() { return oldPosition; }
    public String getTopId()       { return topId; }
    public String getTopName()     { return topName; }
    public String getScore()       { return score; }
    public String getOldScore()    { return oldScore; }
    public String getRewardCount() { return rewardCount; }

    
    public String resolve(@Nullable String text) {
        if (text == null) return "";
        return text
                .replace("{player}",       player)
                .replace("{position}",     position)
                .replace("{old_position}", oldPosition)
                .replace("{top_id}",       topId)
                .replace("{top_name}",     topName)
                .replace("{score}",        score)
                .replace("{old_score}",    oldScore)
                .replace("{reward_count}", rewardCount);
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
