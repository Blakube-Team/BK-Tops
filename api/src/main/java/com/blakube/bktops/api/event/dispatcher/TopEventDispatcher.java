package com.blakube.bktops.api.event.dispatcher;

import com.blakube.bktops.api.event.top.TimedTopResetEvent;
import com.blakube.bktops.api.event.top.TopPositionUpdateEvent;
import com.blakube.bktops.api.timed.ResetSchedule;
import com.blakube.bktops.api.top.TopEntry;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TopEventDispatcher {

    private TopEventDispatcher() {}

    public static void firePositionUpdate(@NotNull String topId,
                                          @NotNull Object identifier,
                                          @Nullable String displayName,
                                          @Nullable Double oldValue,
                                          @Nullable Double newValue,
                                          @Nullable Integer oldPosition,
                                          @Nullable Integer newPosition,
                                          @Nullable String formattedOldValue,
                                          @Nullable String formattedNewValue) {
        boolean async = !Bukkit.isPrimaryThread();
        TopPositionUpdateEvent event = new TopPositionUpdateEvent(
                async, topId, identifier, displayName, oldValue, newValue, oldPosition, newPosition,
                formattedOldValue, formattedNewValue
        );
        Bukkit.getPluginManager().callEvent(event);
    }

    public static void fireTimedReset(@NotNull String topId,
                                      @NotNull ResetSchedule.Type scheduleType,
                                      long previousStartTime,
                                      long newStartTime,
                                      long nextResetTime,
                                      @NotNull List<? extends TopEntry<?>> previousEntries) {
        boolean async = !Bukkit.isPrimaryThread();
        TimedTopResetEvent event = new TimedTopResetEvent(
                async, topId, scheduleType, previousStartTime, newStartTime, nextResetTime, previousEntries
        );
        Bukkit.getPluginManager().callEvent(event);
    }
}
