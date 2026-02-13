package com.blakube.bktops.api.queue;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents an entry in a processing queue.
 */
public final class QueueEntry<K> {

    private final K identifier;
    private final Priority priority;
    private final String reason;
    private final long timestamp;

    public QueueEntry(@NotNull K identifier,
                     @NotNull Priority priority,
                     @NotNull String reason) {
        this.identifier = Objects.requireNonNull(identifier, "identifier cannot be null");
        this.priority = Objects.requireNonNull(priority, "priority cannot be null");
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.timestamp = System.currentTimeMillis();
    }

    @NotNull
    public K getIdentifier() {
        return identifier;
    }

    @NotNull
    public Priority getPriority() {
        return priority;
    }

    @NotNull
    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getWaitTime() {
        return System.currentTimeMillis() - timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueueEntry)) return false;
        QueueEntry<?> that = (QueueEntry<?>) o;
        return Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }

    @Override
    public String toString() {
        return "QueueEntry{" +
                "identifier=" + identifier +
                ", priority=" + priority +
                ", reason='" + reason + '\'' +
                ", waitTime=" + getWaitTime() + "ms" +
                '}';
    }
}