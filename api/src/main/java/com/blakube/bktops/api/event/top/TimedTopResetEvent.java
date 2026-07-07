package com.blakube.bktops.api.event.top;

import com.blakube.bktops.api.timed.ResetSchedule;
import com.blakube.bktops.api.top.TopEntry;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class TimedTopResetEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String topId;
    private final ResetSchedule.Type scheduleType;
    private final long previousStartTime;
    private final long newStartTime;
    private final long nextResetTime;
    private final List<TopEntry<?>> previousEntries;

    public TimedTopResetEvent(boolean async,
                              @NotNull String topId,
                              @NotNull ResetSchedule.Type scheduleType,
                              long previousStartTime,
                              long newStartTime,
                              long nextResetTime,
                              @NotNull List<? extends TopEntry<?>> previousEntries) {
        super(async);
        this.topId = topId;
        this.scheduleType = scheduleType;
        this.previousStartTime = previousStartTime;
        this.newStartTime = newStartTime;
        this.nextResetTime = nextResetTime;
        this.previousEntries = List.copyOf(previousEntries);
    }

    @NotNull
    public String getTopId() { return topId; }

    @NotNull
    public ResetSchedule.Type getScheduleType() { return scheduleType; }

    public long getPreviousStartTime() { return previousStartTime; }

    public long getNewStartTime() { return newStartTime; }

    public long getNextResetTime() { return nextResetTime; }

    @NotNull
    public List<TopEntry<?>> getPreviousEntries() { return previousEntries; }

    @NotNull
    public Optional<TopEntry<?>> getPreviousEntry(int position) {
        if (position < 1 || position > previousEntries.size()) return Optional.empty();
        return Optional.of(previousEntries.get(position - 1));
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
