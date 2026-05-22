package com.blakube.bktops.plugin.formatter;

import com.blakube.bktops.api.config.ConfigContainer;
import org.jetbrains.annotations.NotNull;

public final class TimeValueFormatter implements ValueFormatter {

    private static final String[] UNIT_KEYS       = {"years", "months", "weeks", "days", "hours", "minutes", "seconds"};
    private static final String[] DEFAULT_SUFFIXES = {"y", "mo", "w", "d", "h", "m", "s"};
    private static final long[]   UNIT_IN_SECONDS  = {31_536_000L, 2_592_000L, 604_800L, 86_400L, 3_600L, 60L, 1L};

    private final String separator;
    private final String[] suffixes;
    private final int significantFigures;

    public TimeValueFormatter(@NotNull ConfigContainer config) {
        this.separator         = config.getString("time-format.separator", " ");
        this.significantFigures = Math.max(1, config.getInt("time-format.significant-figures", 3));
        this.suffixes          = new String[UNIT_KEYS.length];
        for (int i = 0; i < UNIT_KEYS.length; i++) {
            suffixes[i] = config.getString("time-format.suffixes." + UNIT_KEYS[i], DEFAULT_SUFFIXES[i]);
        }
    }

    @Override
    public @NotNull String format(double value) {
        long totalSeconds = Math.max(0, (long) value);

        long[] unitValues = new long[UNIT_KEYS.length];
        long remaining = totalSeconds;
        for (int i = 0; i < UNIT_KEYS.length; i++) {
            unitValues[i] = remaining / UNIT_IN_SECONDS[i];
            remaining     %= UNIT_IN_SECONDS[i];
        }

        int start = -1;
        for (int i = 0; i < UNIT_KEYS.length; i++) {
            if (unitValues[i] > 0) {
                start = i;
                break;
            }
        }

        if (start == -1) {
            return "0" + suffixes[UNIT_KEYS.length - 1];
        }

        StringBuilder sb = new StringBuilder();
        int end = Math.min(start + significantFigures, UNIT_KEYS.length);
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) sb.append(separator);
            sb.append(unitValues[i]).append(suffixes[i]);
        }
        return sb.toString();
    }
}
