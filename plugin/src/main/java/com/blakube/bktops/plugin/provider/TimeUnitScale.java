package com.blakube.bktops.plugin.provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unit a placeholder reports bare numbers in when the top is formatted as TIME.
 *
 * <p>Placeholders that already carry units ("3h 20m", "12:30") are unambiguous, but a plain
 * number is not: {@code %statistic_hours_played%} returns hours while {@code %statistic_time_played%}
 * returns ticks. Without this the value was always read as seconds, so 27 hours rendered as "27s".
 */
public enum TimeUnitScale {

    TICKS(0.05d),
    MILLIS(0.001d),
    SECONDS(1d),
    MINUTES(60d),
    HOURS(3_600d),
    DAYS(86_400d);

    private final double seconds;

    TimeUnitScale(double seconds) {
        this.seconds = seconds;
    }

    /** Converts a bare value expressed in this unit to seconds. */
    public double toSeconds(double value) {
        return value * seconds;
    }

    /**
     * Resolves the configured {@code time-unit}, falling back to a guess based on the placeholder
     * name when it is absent. Returns {@link #SECONDS} when nothing matches, which keeps the
     * previous behaviour for placeholders that really do report seconds.
     */
    @NotNull
    public static TimeUnitScale resolve(@Nullable String configured, @NotNull String placeholder) {
        if (configured != null && !configured.isBlank()) {
            try {
                return valueOf(configured.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to inference
            }
        }
        return infer(placeholder);
    }

    @NotNull
    private static TimeUnitScale infer(@NotNull String placeholder) {
        String lower = placeholder.toLowerCase();
        if (lower.contains("hour"))                        return HOURS;
        if (lower.contains("minute") || lower.contains("_min")) return MINUTES;
        if (lower.contains("day"))                         return DAYS;
        if (lower.contains("tick"))                        return TICKS;
        if (lower.contains("millis") || lower.contains("_ms")) return MILLIS;
        return SECONDS;
    }
}
