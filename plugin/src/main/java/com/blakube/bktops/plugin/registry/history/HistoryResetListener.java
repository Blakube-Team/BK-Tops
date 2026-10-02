package com.blakube.bktops.plugin.registry.history;

import com.blakube.bktops.api.event.top.TimedTopResetEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public final class HistoryResetListener implements Listener {

    private final HistoryRegistry registry;

    public HistoryResetListener(@NotNull HistoryRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onTimedTopReset(TimedTopResetEvent event) {
        if (event.getPreviousEntries().isEmpty()) return;
        registry.writeReset(
                event.getTopId(),
                event.getNewStartTime(),
                event.getScheduleType().name(),
                event.getPreviousEntries()
        );
    }
}
