package com.blakube.bktops.plugin.notification;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;










public final class NotificationCases {

    private NotificationCases() {}

    



    @Nullable
    public static ConfigurationSection firstMatching(@Nullable ConfigurationSection cases,
                                                     @NotNull EventContext ctx) {
        if (cases == null) return null;
        for (String key : cases.getKeys(false)) {
            ConfigurationSection c = cases.getConfigurationSection(key);
            if (c == null || !c.getBoolean("enabled", true)) continue;
            ConfigurationSection when = c.getConfigurationSection("when");
            if (when == null || matches(when, ctx)) return c;
        }
        return null;
    }

    
    public static boolean matches(@NotNull ConfigurationSection when, @NotNull EventContext ctx) {
        Integer pos = toInt(ctx.getPosition());
        Integer old = toInt(ctx.getOldPosition());
        boolean oldIsNone = ctx.getOldPosition().isEmpty();

        if (when.contains("position")
                && (pos == null || pos != when.getInt("position"))) {
            return false;
        }

        if (when.contains("old-position")) {
            Object spec = when.get("old-position");
            if (spec instanceof String s) {
                if (s.equalsIgnoreCase("none") && !oldIsNone) return false;
            } else if (spec instanceof Integer expected) {
                if (old == null || !old.equals(expected)) return false;
            } else if (spec instanceof ConfigurationSection oldSec
                    && oldSec.contains("not-equals")
                    && old != null && old == oldSec.getInt("not-equals")) {
                return false;
            }
        }

        if (when.contains("position-range") && !inRange(pos, when.getIntegerList("position-range"))) {
            return false;
        }

        if (when.contains("old-position-range") && !inRange(old, when.getIntegerList("old-position-range"))) {
            return false;
        }

        if (when.contains("improved")) {
            boolean improved = old != null && pos != null && old > pos;
            if (when.getBoolean("improved") != improved) return false;
        }

        if (when.contains("worsened")) {
            boolean worsened = old != null && pos != null && old < pos;
            if (when.getBoolean("worsened") != worsened) return false;
        }

        return true;
    }

    private static boolean inRange(@Nullable Integer value, @NotNull List<Integer> range) {
        return value != null && range.size() >= 2 && value >= range.get(0) && value <= range.get(1);
    }

    @Nullable
    private static Integer toInt(@Nullable String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
