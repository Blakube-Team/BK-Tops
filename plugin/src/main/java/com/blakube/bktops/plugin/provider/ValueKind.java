package com.blakube.bktops.plugin.provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;






public enum ValueKind {
    
    TIME,
    
    NUMBER,
    
    UNKNOWN;

    




    @NotNull
    public static ValueKind fromValueFormat(@Nullable String format) {
        if (format == null) return UNKNOWN;
        return switch (format.trim().toUpperCase()) {
            case "TIME" -> TIME;
            case "EXACT", "ROUNDED", "COMPACT", "COMPACT_ROUNDED" -> NUMBER;
            default -> UNKNOWN;
        };
    }
}
