package com.blakube.bktops.api.timed;

import com.blakube.bktops.api.top.Top;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Represents a timed leaderboard that resets periodically.
 */
public interface TimedTop<K> extends Top<K> {

    @NotNull
    ResetSchedule getResetSchedule();

    boolean shouldReset();

    @NotNull
    Duration getTimeUntilReset();

    long getNextResetTime();

    long getStartTime();
}