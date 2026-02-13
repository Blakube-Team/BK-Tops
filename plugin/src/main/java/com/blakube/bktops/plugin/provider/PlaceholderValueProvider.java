package com.blakube.bktops.plugin.provider;

import com.blakube.bktops.api.provider.ValueProvider;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlaceholderValueProvider implements ValueProvider<UUID> {

    private final Plugin plugin;
    private final String placeholder;
    private static final long DEFAULT_TTL_MILLIS = 500L;
    private static final int MAX_CACHE_SIZE = 10_000;
    private final long ttlMillis = DEFAULT_TTL_MILLIS;
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicInteger cacheSize = new AtomicInteger(0);
    private volatile boolean recursionWarned = false;

    public PlaceholderValueProvider(@NotNull Plugin plugin, @NotNull String placeholder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
    }

    @Override
    public @Nullable Double getValue(@NotNull UUID identifier) {
        long now = System.currentTimeMillis();
        CacheEntry ce = cache.get(identifier);
        if (ce != null && (now - ce.time) <= ttlMillis) {
            return ce.value;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(identifier);
        try {
            Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi == null || !papi.isEnabled()) {
                return null;
            }

            String lower = placeholder.toLowerCase();
            if (lower.contains("%bktops_") || lower.contains("% bktops_")) {
                if (!recursionWarned) {
                    recursionWarned = true;
                    plugin.getLogger().warning("[BK-Tops] Detected BK-Tops placeholder configured as provider (" + placeholder + "). " +
                            "This causes recursion and zeros. Please use a base placeholder (e.g., Vault balance) instead.");
                }
                return null;
            }

            String str = PlaceholderAPI.setPlaceholders(offline, placeholder);
            if (str == null) return null;
            str = str.trim();
            if (str.isEmpty()) return null;

            Double parsed = parseNumeric(str);
            if (parsed != null) putCache(identifier, parsed, now);
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void putCache(UUID id, double value, long now) {
        if (cacheSize.get() >= MAX_CACHE_SIZE && !cache.containsKey(id)) {
            // simple random eviction: remove some entries to make room
            int toRemove = Math.max(1, MAX_CACHE_SIZE / 100);
            int removed = 0;
            for (UUID key : cache.keySet()) {
                if (cache.remove(key) != null) {
                    removed++;
                    if (removed >= toRemove) break;
                }
            }
            cacheSize.addAndGet(-removed);
        }
        CacheEntry prev = cache.put(id, new CacheEntry(value, now));
        if (prev == null) cacheSize.incrementAndGet();
    }

    private static Double parseNumeric(String s) {
        String original = s;
        if (s.contains(":")) {
            String[] parts = s.split(":");
            long seconds = 0;
            try {
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

        s = s.replace(",", "");
        s = s.replace("_", "");
        s = s.trim();

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
                    default -> 1.0;
                };
                s = s.substring(0, s.length() - 1).trim();
            }
        }

        s = s.replaceAll("[^0-9eE+\\-\\.]", "");
        if (s.isEmpty() || s.equals("-") || s.equals("+")) return null;

        double base = Double.parseDouble(s);
        double val = base * multiplier;
        if (percent) val = val / 100.0;
        if (Double.isInfinite(val) || Double.isNaN(val)) return null;
        return val;
    }

    private static final class CacheEntry {
        final double value;
        final long time;
        CacheEntry(double value, long time) {
            this.value = value;
            this.time = time;
        }
    }

    @Override
    public @NotNull String getName() {
        return "PAPI[" + placeholder + "]";
    }

    @Override
    public boolean isAvailable() {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        return papi != null && papi.isEnabled();
    }
}
