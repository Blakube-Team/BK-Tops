package com.blakube.bktops.plugin.notification;

import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.event.top.TimedTopResetEvent;
import com.blakube.bktops.api.event.top.TopPositionUpdateEvent;
import com.blakube.bktops.api.top.Top;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;






public final class TopNotificationListener implements Listener {

    private final NotificationService notifications;

    public TopNotificationListener(@NotNull NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventHandler
    public void onTimedTopReset(TimedTopResetEvent event) {
        notifications.notifyTimedTopReset(EventContext.timedReset(event.getTopId(), event.getTopId()));
    }

    @EventHandler
    public void onTopPositionUpdate(TopPositionUpdateEvent event) {
        Integer newPos = event.getNewPosition();
        Integer oldPos = event.getOldPosition();

        
        
        if (newPos == null || newPos <= 0) return;
        if (oldPos != null && oldPos.equals(newPos)) return;

        Top<?> top = TopAPIProvider.isAvailable()
                ? TopAPIProvider.getInstance().getTop(event.getTopId())
                : null;
        if (top == null) return;

        int size = top.getCurrentSize();
        if (newPos > size) return;

        boolean wasUnranked = oldPos == null || oldPos <= 0;

        
        
        
        if (newPos == size && wasUnranked) return;

        String newPosStr = String.valueOf(newPos);
        String oldPosStr = wasUnranked ? "" : String.valueOf(oldPos);

        EventContext ctx = EventContext.positionUpdate(
                event.getDisplayName(),
                newPosStr,
                oldPosStr,
                event.getTopId(),
                event.getTopId(),
                event.getFormattedNewValue() != null ? event.getFormattedNewValue() : "0",
                event.getFormattedOldValue() != null ? event.getFormattedOldValue() : "0"
        );

        notifications.notifyTopPositionUpdate(ctx);
    }
}
