package com.blakube.bktops.plugin.provider;

import com.blakube.bktops.api.provider.ValueProvider;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderValueProvider implements ValueProvider<UUID> {

    private final Plugin plugin;
    private final String placeholder;
    private final Plugin papiPlugin;
    private final boolean hasRecursion;

    
    
    private static final long DEFAULT_TTL_MILLIS = 2_000L;
    private static final int  MAX_CACHE_SIZE     = 10_000;

    
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>(256);

    private final java.util.concurrent.atomic.AtomicBoolean recursionWarned = new java.util.concurrent.atomic.AtomicBoolean(false);

    public PlaceholderValueProvider(@NotNull Plugin plugin, @NotNull String placeholder) {
        this.plugin      = Objects.requireNonNull(plugin,      "plugin");
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");

        this.papiPlugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");

        String lower = placeholder.toLowerCase();
        this.hasRecursion = lower.contains("%bktops_") || lower.contains("% bktops_");
    }

    @Override
    public @Nullable Double getValue(@NotNull UUID identifier) {
        if (papiPlugin == null || !papiPlugin.isEnabled()) return null;

        if (hasRecursion) {
            if (recursionWarned.compareAndSet(false, true)) {
                plugin.getLogger().warning("[BK-Tops] Detected BK-Tops placeholder configured as provider ("
                        + placeholder + "). This causes recursion and zeros. "
                        + "Please use a base placeholder (e.g., Vault balance) instead.");
            }
            return null;
        }

        long now = System.currentTimeMillis();
        CacheEntry ce = cache.get(identifier);
        if (ce != null && (now - ce.time) <= DEFAULT_TTL_MILLIS) {
            return ce.value;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(identifier);
        try {
            String str = PlaceholderAPI.setPlaceholders(offline, placeholder);
            if (str == null) return null;
            str = str.trim();
            if (str.isEmpty()) return null;

            Double parsed = parseNumeric(str);
            if (parsed != null) {
                if (cache.size() >= MAX_CACHE_SIZE) evictExpired(now);
                cache.put(identifier, new CacheEntry(parsed, now));
            }
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void evictExpired(long now) {
        cache.entrySet().removeIf(e -> (now - e.getValue().time) > DEFAULT_TTL_MILLIS);
    }

    private static final Pattern TIME_UNIT_TRIGGER = Pattern.compile("\\d+\\s*[dDhH]");
    private static final Pattern TIME_UNIT_FULL    = Pattern.compile(
            "^\\s*(?:(\\d+)\\s*[dD]\\s*)?(?:(\\d+)\\s*[hH]\\s*)?(?:(\\d+)\\s*[mM]\\s*)?(?:(\\d+)\\s*[sS])?\\s*$"
    );

    private static Double parseNumeric(String s) {
        if (s.contains(":")) {
            String[] parts = s.split(":");
            try {
                long seconds = 0;
                if (parts.length == 2) {
                    seconds = Long.parseLong(parts[0].replaceAll("[^0-9]", "")) * 60
                            + Long.parseLong(parts[1].replaceAll("[^0-9]", ""));
                } else if (parts.length == 3) {
                    seconds = Long.parseLong(parts[0].replaceAll("[^0-9]", "")) * 3600
                            + Long.parseLong(parts[1].replaceAll("[^0-9]", "")) * 60
                            + Long.parseLong(parts[2].replaceAll("[^0-9]", ""));
                }
                return (double) seconds;
            } catch (NumberFormatException ignored) {
            }
        }

        if (TIME_UNIT_TRIGGER.matcher(s).find()) {
            Matcher m = TIME_UNIT_FULL.matcher(s);
            if (m.matches()) {
                long seconds = 0;
                if (m.group(1) != null) seconds += Long.parseLong(m.group(1)) * 86_400;
                if (m.group(2) != null) seconds += Long.parseLong(m.group(2)) * 3_600;
                if (m.group(3) != null) seconds += Long.parseLong(m.group(3)) * 60;
                if (m.group(4) != null) seconds += Long.parseLong(m.group(4));
                return (double) seconds;
            }
        }

        s = s.replace(",", "").replace("_", "").trim();

        boolean percent = s.endsWith("%");
        if (percent) s = s.substring(0, s.length() - 1).trim();

        double multiplier = 1.0;
        if (!s.isEmpty()) {
            char last = Character.toLowerCase(s.charAt(s.length() - 1));
            if (last == 'k' || last == 'm' || last == 'b' || last == 't') {
                multiplier = switch (last) {
                    case 'k' -> 1_000d;
                    case 'm' -> 1_000_000d;
                    case 'b' -> 1_000_000_000d;
                    case 't' -> 1_000_000_000_000d;
                    default  -> 1.0;
                };
                s = s.substring(0, s.length() - 1).trim();
            }
        }

        s = s.replaceAll("[^0-9eE+\\-\\.]", "");
        if (s.isEmpty() || s.equals("-") || s.equals("+")) return null;
        if (!s.chars().anyMatch(Character::isDigit)) return null;

        double base = Double.parseDouble(s);
        double val  = base * multiplier;
        if (percent) val /= 100.0;
        if (Double.isInfinite(val) || Double.isNaN(val)) return null;
        return val;
    }

    private static final class CacheEntry {
        final double value;
        final long   time;
        CacheEntry(double value, long time) { this.value = value; this.time = time; }
    }

    @Override
    public @NotNull String getName() { return "PAPI[" + placeholder + "]"; }

    @Override
    public boolean isAvailable() { return papiPlugin != null && papiPlugin.isEnabled(); }
}
