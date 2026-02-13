package com.blakube.bktops.api.event.dispatcher;

import com.blakube.bktops.api.event.top.TimedTopResetEvent;
import com.blakube.bktops.api.event.top.TopPositionUpdateEvent;
import com.blakube.bktops.api.timed.ResetSchedule;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Central dispatcher for BK-Tops API events. Addons can listen to these Bukkit events.
 */
public final class TopEventDispatcher {

    private TopEventDispatcher() {}

    public static void firePositionUpdate(@NotNull String topId,
                                          @NotNull Object identifier,
                                          @Nullable String displayName,
                                          @Nullable Double oldValue,
                                          @Nullable Double newValue,
                                          @Nullable Integer oldPosition,
                                          @Nullable Integer newPosition) {
        boolean async = !Bukkit.isPrimaryThread();
        TopPositionUpdateEvent event = new TopPositionUpdateEvent(
                async, topId, identifier, displayName, oldValue, newValue, oldPosition, newPosition
        );
        Bukkit.getPluginManager().callEvent(event);
    }

    public static void fireTimedReset(@NotNull String topId,
                                      @NotNull ResetSchedule.Type scheduleType,
                                      long previousStartTime,
                                      long newStartTime,
                                      long nextResetTime) {
        boolean async = !Bukkit.isPrimaryThread();
        TimedTopResetEvent event = new TimedTopResetEvent(
                async, topId, scheduleType, previousStartTime, newStartTime, nextResetTime
        );
        Bukkit.getPluginManager().callEvent(event);
    }
}
