package com.blakube.bktops.api.top;

import com.blakube.bktops.api.processor.TopProcessor;
import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.queue.ProcessingQueue;
import com.blakube.bktops.api.storage.config.TopConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Represents a leaderboard.
 */
public interface Top<K> {

    @NotNull
    String getId();

    @NotNull
    TopConfig getConfig();

    @NotNull
    List<TopEntry<K>> getEntries();

    @NotNull
    Optional<TopEntry<K>> getEntry(int position);

    int getPosition(@NotNull K identifier);

    boolean isInTop(@NotNull K identifier);

    @NotNull
    Optional<Double> getMinValue();

    @NotNull
    ValueProvider<K> getValueProvider();

    @NotNull
    Optional<Double> getMaxValue();

    int getCurrentSize();

    void markDirty(@NotNull K identifier, @NotNull String reason);

    void enqueue(@NotNull Collection<K> identifiers,
                @NotNull Priority priority,
                @NotNull String reason);

    @NotNull
    ProcessingQueue<K> getQueue();

    @NotNull
    TopProcessor<K> getProcessor();

    void refresh();

    void reset();
}