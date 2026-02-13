package com.blakube.bktops.api.timed;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/**
 * Defines when a timed leaderboard should reset.
 */
public final class ResetSchedule {

    private final Type type;
    private final Duration interval;

    private ResetSchedule(@NotNull Type type, @NotNull Duration interval) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.interval = Objects.requireNonNull(interval, "interval cannot be null");
    }

    @NotNull
    public Type getType() {
        return type;
    }

    @NotNull
    public Duration getInterval() {
        return interval;
    }

    @NotNull
    public static ResetSchedule hourly() {
        return new ResetSchedule(Type.HOURLY, Duration.ofHours(1));
    }

    @NotNull
    public static ResetSchedule daily() {
        return new ResetSchedule(Type.DAILY, Duration.ofDays(1));
    }

    @NotNull
    public static ResetSchedule weekly() {
        return new ResetSchedule(Type.WEEKLY, Duration.ofDays(7));
    }

    @NotNull
    public static ResetSchedule monthly() {
        return new ResetSchedule(Type.MONTHLY, Duration.ofDays(30));
    }

    @NotNull
    public static ResetSchedule custom(@NotNull Duration interval) {
        Objects.requireNonNull(interval, "interval cannot be null");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        return new ResetSchedule(Type.CUSTOM, interval);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResetSchedule)) return false;
        ResetSchedule that = (ResetSchedule) o;
        return type == that.type && Objects.equals(interval, that.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, interval);
    }

    @Override
    public String toString() {
        if (type == Type.CUSTOM) {
            return "ResetSchedule{type=" + type + ", interval=" + interval + "}";
        }
        return "ResetSchedule{type=" + type + "}";
    }

    public enum Type {
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY,
        CUSTOM
    }
}