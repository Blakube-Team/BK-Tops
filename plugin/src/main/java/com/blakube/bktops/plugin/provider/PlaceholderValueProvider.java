package com.blakube.bktops.plugin.provider;

import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.plugin.debug.Debug;
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

public final class PlaceholderValueProvider implements ValueProvider<UUID>, DetectableValueKind {

    private final Plugin plugin;
    private final String placeholder;
    private final Plugin papiPlugin;
    private final boolean hasRecursion;

    
    private final ValueKind parseHint;

    private volatile ValueKind detectedKind = ValueKind.UNKNOWN;

    
    
    private static final long DEFAULT_TTL_MILLIS = 2_000L;
    private static final int  MAX_CACHE_SIZE     = 10_000;

    
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>(256);

    private final java.util.concurrent.atomic.AtomicBoolean recursionWarned = new java.util.concurrent.atomic.AtomicBoolean(false);

    public PlaceholderValueProvider(@NotNull Plugin plugin, @NotNull String placeholder) {
        this(plugin, placeholder, ValueKind.UNKNOWN);
    }

    public PlaceholderValueProvider(@NotNull Plugin plugin, @NotNull String placeholder, @NotNull ValueKind parseHint) {
        this.plugin      = Objects.requireNonNull(plugin,      "plugin");
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
        this.parseHint   = Objects.requireNonNull(parseHint,   "parseHint");

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
            if (str == null) {
                Debug.log(() -> "PAPI returned null for " + placeholder + " (player " + identifier + ")");
                return null;
            }
            str = str.trim();
            if (str.isEmpty()) {
                Debug.log(() -> "PAPI returned empty for " + placeholder + " (player " + identifier + ")");
                return null;
            }

            final String raw = str;
            ParsedValue parsed = parse(str, parseHint);
            if (parsed != null) {
                updateDetectedKind(parsed.kind);
                if (cache.size() >= MAX_CACHE_SIZE) evictExpired(now);
                cache.put(identifier, new CacheEntry(parsed.value, now));
                Debug.log(() -> "Parsed " + placeholder + " = \"" + raw + "\" -> " + parsed.value
                        + " (" + parsed.kind + ", hint=" + parseHint + ") for " + identifier);
                return parsed.value;
            }
            Debug.log(() -> "Could not parse " + placeholder + " = \"" + raw + "\" (hint=" + parseHint
                    + ") for " + identifier);
            return null;
        } catch (NumberFormatException e) {
            Debug.log(() -> "NumberFormatException parsing " + placeholder + " for " + identifier);
            return null;
        }
    }

    




    private void updateDetectedKind(@NotNull ValueKind kind) {
        if (kind == ValueKind.TIME) {
            detectedKind = ValueKind.TIME;
        } else if (detectedKind != ValueKind.TIME) {
            detectedKind = ValueKind.NUMBER;
        }
    }

    @Override
    public @NotNull ValueKind getDetectedValueKind() {
        return detectedKind;
    }

    private void evictExpired(long now) {
        cache.entrySet().removeIf(e -> (now - e.getValue().time) > DEFAULT_TTL_MILLIS);
    }

    private static final Pattern COLON_TIME = Pattern.compile("^\\d{1,3}:[0-5]?\\d(?::[0-5]?\\d)?$");

    
    
    
    private static final Pattern DURATION_TOKEN = Pattern.compile(
            "(\\d+)\\s*(mo|[ywdhms])", Pattern.CASE_INSENSITIVE);

    
    
    private static long secondsForUnit(@NotNull String lowerUnit) {
        return switch (lowerUnit) {
            case "y"  -> 31_536_000L;
            case "mo" -> 2_592_000L;
            case "w"  -> 604_800L;
            case "d"  -> 86_400L;
            case "h"  -> 3_600L;
            case "m"  -> 60L;
            default   -> 1L; 
        };
    }

    
    private static ParsedValue parse(String s) {
        return parse(s, ValueKind.UNKNOWN);
    }

    














    private static ParsedValue parse(String s, @NotNull ValueKind hint) {
        if (hint != ValueKind.NUMBER) {
            Double colonSeconds = parseColon(s);
            if (colonSeconds != null) return new ParsedValue(colonSeconds, ValueKind.TIME);

            Long durationSeconds = parseDuration(s, hint);
            if (durationSeconds != null) return new ParsedValue(durationSeconds, ValueKind.TIME);

            if (hint == ValueKind.TIME) {
                
                ParsedValue number = parseNumber(s, false);
                return number == null ? null : new ParsedValue(number.value, ValueKind.TIME);
            }
        }

        return parseNumber(s, hint == ValueKind.NUMBER);
    }

    









    @Nullable
    private static Long parseDuration(@NotNull String s, @NotNull ValueKind hint) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;

        Matcher m = DURATION_TOKEN.matcher(trimmed);
        long totalSeconds = 0;
        int tokenCount = 0;
        int cursor = 0;
        String lastUnitRaw = null;

        while (m.find()) {
            if (!isSeparator(trimmed, cursor, m.start())) return null; 
            cursor = m.end();
            lastUnitRaw = m.group(2);
            totalSeconds += Long.parseLong(m.group(1)) * secondsForUnit(lastUnitRaw.toLowerCase());
            tokenCount++;
        }

        if (tokenCount == 0) return null;
        if (!isSeparator(trimmed, cursor, trimmed.length())) return null; 

        
        if (tokenCount == 1 && hint != ValueKind.TIME && "M".equals(lastUnitRaw)) return null;

        return totalSeconds;
    }

    
    private static boolean isSeparator(@NotNull String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c != ',' && !Character.isWhitespace(c)) return false;
        }
        return true;
    }

    




    @Nullable
    private static ParsedValue parseNumber(String s, boolean allowLowerMinuteSuffix) {
        s = s.replace(",", "").replace("_", "").trim();

        boolean percent = s.endsWith("%");
        if (percent) s = s.substring(0, s.length() - 1).trim();

        double multiplier = 1.0;
        if (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            multiplier = switch (last) {
                case 'k', 'K' -> 1_000d;
                case 'M'      -> 1_000_000d;
                case 'm'      -> allowLowerMinuteSuffix ? 1_000_000d : 1.0;
                case 'b', 'B' -> 1_000_000_000d;
                case 't', 'T' -> 1_000_000_000_000d;
                default       -> 1.0;
            };
            if (multiplier != 1.0) s = s.substring(0, s.length() - 1).trim();
        }

        s = s.replaceAll("[^0-9eE+\\-\\.]", "");
        if (s.isEmpty() || s.equals("-") || s.equals("+")) return null;
        if (!s.chars().anyMatch(Character::isDigit)) return null;

        double base = Double.parseDouble(s);
        double val  = base * multiplier;
        if (percent) val /= 100.0;
        if (Double.isInfinite(val) || Double.isNaN(val)) return null;
        return new ParsedValue(val, ValueKind.NUMBER);
    }

    @Nullable
    private static Double parseColon(String s) {
        String trimmed = s.trim();
        if (!COLON_TIME.matcher(trimmed).matches()) return null;
        String[] parts = trimmed.split(":");
        try {
            if (parts.length == 2) {
                return (double) (Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]));
            }
            return (double) (Long.parseLong(parts[0]) * 3600
                    + Long.parseLong(parts[1]) * 60
                    + Long.parseLong(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ParsedValue(double value, ValueKind kind) {}

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
