package com.blakube.bktops.plugin.reward;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum TeamRewardMode {
    ENTRY,
    MEMBERS;

    @NotNull
    public static TeamRewardMode fromConfig(@Nullable String raw, @NotNull TeamRewardMode fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return switch (raw.trim().toLowerCase()) {
            case "members", "all", "team" -> MEMBERS;
            case "entry", "single", "leader" -> ENTRY;
            default -> fallback;
        };
    }
}
