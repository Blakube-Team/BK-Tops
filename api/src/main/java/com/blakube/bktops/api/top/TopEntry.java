package com.blakube.bktops.api.top;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents an entry in a leaderboard.
 */
public final class TopEntry<K> implements Comparable<TopEntry<K>> {

    private final K identifier;
    private final String displayName;
    private final double value;
    private final int position;
    private final long lastUpdated;

    public TopEntry(@NotNull K identifier,
                    @NotNull String displayName,
                    double value,
                    int position) {
        this(identifier, displayName, value, position, System.currentTimeMillis());
    }

    public TopEntry(@NotNull K identifier,
                    @NotNull String displayName,
                    double value,
                    int position,
                    long lastUpdated) {
        this.identifier = Objects.requireNonNull(identifier, "identifier cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.value = value;
        this.position = position;
        this.lastUpdated = lastUpdated;
    }

    @NotNull
    public K getIdentifier() {
        return identifier;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    public double getValue() {
        return value;
    }

    public int getPosition() {
        return position;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    @NotNull
    public TopEntry<K> withPosition(int newPosition) {
        return new TopEntry<>(identifier, displayName, value, newPosition, lastUpdated);
    }

    @NotNull
    public TopEntry<K> withValue(double newValue) {
        return new TopEntry<>(identifier, displayName, newValue, position, System.currentTimeMillis());
    }

    @Override
    public int compareTo(@NotNull TopEntry<K> other) {
        return Double.compare(other.value, this.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TopEntry)) return false;
        TopEntry<?> topEntry = (TopEntry<?>) o;
        return Objects.equals(identifier, topEntry.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }

    @Override
    public String toString() {
        return "TopEntry{" +
                "identifier=" + identifier +
                ", displayName='" + displayName + '\'' +
                ", value=" + value +
                ", position=" + position +
                '}';
    }
}