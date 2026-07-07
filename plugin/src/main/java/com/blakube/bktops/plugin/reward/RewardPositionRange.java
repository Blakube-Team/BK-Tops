package com.blakube.bktops.plugin.reward;

import org.jetbrains.annotations.NotNull;

public record RewardPositionRange(int start, int end) {

    public RewardPositionRange {
        if (start <= 0 || end < start) {
            throw new IllegalArgumentException("Invalid reward position range: " + start + "-" + end);
        }
    }

    public boolean matches(int position) {
        return position >= start && position <= end;
    }

    @NotNull
    public static RewardPositionRange parse(@NotNull String raw) {
        String value = raw.trim();
        if (value.contains("-")) {
            String[] split = value.split("-", 2);
            return new RewardPositionRange(Integer.parseInt(split[0].trim()), Integer.parseInt(split[1].trim()));
        }
        int position = Integer.parseInt(value);
        return new RewardPositionRange(position, position);
    }
}
