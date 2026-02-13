package com.blakube.bktops.api.processor;

import org.jetbrains.annotations.NotNull;

/**
 * Processes queued identifiers for a leaderboard.
 */
public interface TopProcessor<K> {

    int processBatch(int batchSize);

    void processImmediate(@NotNull K identifier, @NotNull String reason);

    boolean isEnabled();

    void setEnabled(boolean enabled);
}