package com.blakube.bktops.api.queue;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Queue for processing identifiers with priority support.
 */
public interface ProcessingQueue<K> {

    boolean enqueue(@NotNull K identifier,
                   @NotNull Priority priority,
                   @NotNull String reason);

    int enqueueAll(@NotNull Collection<K> identifiers,
                   @NotNull Priority priority,
                   @NotNull String reason);

    @NotNull
    List<QueueEntry<K>> poll(int maxCount);

    boolean isEmpty();

    int size();

    int size(@NotNull Priority priority);

    boolean contains(@NotNull K identifier);

    void clear();

    void clear(@NotNull Priority priority);
}