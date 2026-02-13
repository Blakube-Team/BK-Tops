package com.blakube.bktops.api.event.top;

import com.blakube.bktops.api.timed.ResetSchedule;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a timed top resets.
 * This event may be called asynchronously.
 */
public class TimedTopResetEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String topId;
    private final ResetSchedule.Type scheduleType;
    private final long previousStartTime;
    private final long newStartTime;
    private final long nextResetTime;

    public TimedTopResetEvent(boolean async,
                              @NotNull String topId,
                              @NotNull ResetSchedule.Type scheduleType,
                              long previousStartTime,
                              long newStartTime,
                              long nextResetTime) {
        super(async);
        this.topId = topId;
        this.scheduleType = scheduleType;
        this.previousStartTime = previousStartTime;
        this.newStartTime = newStartTime;
        this.nextResetTime = nextResetTime;
    }

    @NotNull
    public String getTopId() { return topId; }

    @NotNull
    public ResetSchedule.Type getScheduleType() { return scheduleType; }

    public long getPreviousStartTime() { return previousStartTime; }

    public long getNewStartTime() { return newStartTime; }

    public long getNextResetTime() { return nextResetTime; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
