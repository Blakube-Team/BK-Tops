package com.blakube.bktops.plugin.provider;

import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.plugin.debug.Debug;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final @Nullable StatisticQuery statisticQuery;


    private final ValueKind parseHint;


    private final TimeUnitScale bareTimeUnit;

    private volatile ValueKind detectedKind = ValueKind.UNKNOWN;

    
    
    private static final long DEFAULT_TTL_MILLIS = 2_000L;
    private static final int  MAX_CACHE_SIZE     = 10_000;

    
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>(256);

    private final java.util.concurrent.atomic.AtomicBoolean recursionWarned = new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final Material[] BLOCK_MATERIALS = buildMaterials(Material::isBlock);
    private static final Material[] ITEM_MATERIALS = buildMaterials(Material::isItem);
    private static final EntityType[] ENTITY_TYPES = buildEntityTypes();

    private static final ConcurrentHashMap<Statistic, java.util.Set<Material>> INVALID_MATERIALS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Statistic, java.util.Set<EntityType>> INVALID_ENTITIES = new ConcurrentHashMap<>();

    public PlaceholderValueProvider(@NotNull Plugin plugin, @NotNull String placeholder) {
        this(plugin, placeholder, ValueKind.UNKNOWN);
    }

    public PlaceholderValueProvider(@NotNull Plugin plugin, @NotNull String placeholder, @NotNull ValueKind parseHint) {
        this(plugin, placeholder, parseHint, TimeUnitScale.SECONDS);
    }

    public PlaceholderValueProvider(@NotNull Plugin plugin,
                                    @NotNull String placeholder,
                                    @NotNull ValueKind parseHint,
                                    @NotNull TimeUnitScale bareTimeUnit) {
        this.plugin       = Objects.requireNonNull(plugin,       "plugin");
        this.placeholder  = Objects.requireNonNull(placeholder,  "placeholder");
        this.parseHint    = Objects.requireNonNull(parseHint,    "parseHint");
        this.bareTimeUnit = Objects.requireNonNull(bareTimeUnit, "bareTimeUnit");

        this.papiPlugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");

        this.hasRecursion = hasProviderRecursion(placeholder);
        this.statisticQuery = parseStatisticQuery(placeholder);
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
            String str = statisticQuery != null
                    ? resolveStatistic(offline, statisticQuery)
                    : PlaceholderAPI.setPlaceholders(offline, placeholder);
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
            ParsedValue parsed = parse(str, parseHint, bareTimeUnit);
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

    private static @Nullable StatisticQuery parseStatisticQuery(@NotNull String placeholder) {
        String normalized = normalizePlaceholder(placeholder);
        if (!normalized.startsWith("%statistic_") || !normalized.endsWith("%")) return null;

        String identifier = normalized.substring("%statistic_".length(), normalized.length() - 1).trim();
        if (identifier.isEmpty()) return null;

        int colonIndex = identifier.indexOf(':');
        String statisticName = (colonIndex < 0 ? identifier : identifier.substring(0, colonIndex)).trim();
        if (statisticName.isEmpty()) return null;

        Statistic statistic;
        try {
            statistic = Statistic.valueOf(statisticName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        Statistic.Type type = statistic.getType();
        if (type != Statistic.Type.BLOCK && type != Statistic.Type.ITEM && type != Statistic.Type.ENTITY) {
            return null;
        }

        if (colonIndex < 0) {
            return StatisticQuery.aggregate(statistic, type);
        }

        String targetsRaw = identifier.substring(colonIndex + 1).trim();
        if (targetsRaw.isEmpty()) {
            return StatisticQuery.aggregate(statistic, type);
        }

        String[] tokens = targetsRaw.split(",");
        if (type == Statistic.Type.BLOCK || type == Statistic.Type.ITEM) {
            List<Material> materials = new ArrayList<>(tokens.length);
            for (String token : tokens) {
                Material material = toMaterial(token);
                if (material != null) materials.add(material);
            }
            if (materials.isEmpty()) return null;
            return StatisticQuery.materialTargets(statistic, type, materials.toArray(Material[]::new));
        }

        List<EntityType> entities = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            EntityType entityType = toEntityType(token);
            if (entityType != null) entities.add(entityType);
        }
        if (entities.isEmpty()) return null;
        return StatisticQuery.entityTargets(statistic, entities.toArray(EntityType[]::new));
    }

    private static @Nullable String resolveStatistic(@NotNull OfflinePlayer player, @NotNull StatisticQuery query) {
        return switch (query.type()) {
            case BLOCK, ITEM -> resolveMaterialStatistic(player, query);
            case ENTITY -> resolveEntityStatistic(player, query);
            default -> null;
        };
    }

    private static @NotNull String resolveMaterialStatistic(@NotNull OfflinePlayer player, @NotNull StatisticQuery query) {
        Material[] source = query.aggregateAll() ? (query.type() == Statistic.Type.BLOCK ? BLOCK_MATERIALS : ITEM_MATERIALS)
                : query.materials();

        long total = 0L;
        java.util.Set<Material> invalid = INVALID_MATERIALS.computeIfAbsent(query.statistic(), k -> ConcurrentHashMap.newKeySet());
        for (Material material : source) {
            if (invalid.contains(material)) continue;
            try {
                total += readStatistic(player, query.statistic(), material);
            } catch (IllegalArgumentException ignored) {
                invalid.add(material);
            }
        }
        return Long.toString(total);
    }

    private static @NotNull String resolveEntityStatistic(@NotNull OfflinePlayer player, @NotNull StatisticQuery query) {
        EntityType[] source = query.aggregateAll() ? ENTITY_TYPES : query.entities();

        long total = 0L;
        java.util.Set<EntityType> invalid = INVALID_ENTITIES.computeIfAbsent(query.statistic(), k -> ConcurrentHashMap.newKeySet());
        for (EntityType entityType : source) {
            if (invalid.contains(entityType)) continue;
            try {
                total += readStatistic(player, query.statistic(), entityType);
            } catch (IllegalArgumentException ignored) {
                invalid.add(entityType);
            }
        }
        return Long.toString(total);
    }

    private static int readStatistic(@NotNull OfflinePlayer player, @NotNull Statistic statistic, @NotNull Material material) {
        if (player.isOnline() && player.getPlayer() != null) {
            return player.getPlayer().getStatistic(statistic, material);
        }
        return player.getStatistic(statistic, material);
    }

    private static int readStatistic(@NotNull OfflinePlayer player, @NotNull Statistic statistic, @NotNull EntityType entityType) {
        if (player.isOnline() && player.getPlayer() != null) {
            return player.getPlayer().getStatistic(statistic, entityType);
        }
        return player.getStatistic(statistic, entityType);
    }

    private static Material @NotNull [] buildMaterials(@NotNull java.util.function.Predicate<Material> predicate) {
        List<Material> result = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isLegacyMaterial(material)) continue;
            if (predicate.test(material)) result.add(material);
        }
        return result.toArray(Material[]::new);
    }

    private static EntityType @NotNull [] buildEntityTypes() {
        List<EntityType> result = new ArrayList<>();
        for (EntityType entityType : EntityType.values()) {
            if (entityType == EntityType.UNKNOWN) continue;
            if (entityType.name().startsWith("LEGACY_")) continue;
            result.add(entityType);
        }
        return result.toArray(EntityType[]::new);
    }

    private static boolean isLegacyMaterial(@NotNull Material material) {
        return material.name().startsWith("LEGACY_");
    }

    private static @Nullable Material toMaterial(@NotNull String token) {
        String key = token.trim();
        if (key.isEmpty()) return null;
        int namespace = key.indexOf(':');
        if (namespace >= 0 && namespace + 1 < key.length()) {
            key = key.substring(namespace + 1);
        }
        try {
            Material material = Material.valueOf(key.toUpperCase(Locale.ROOT));
            return isLegacyMaterial(material) ? null : material;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @Nullable EntityType toEntityType(@NotNull String token) {
        String key = token.trim();
        if (key.isEmpty()) return null;
        int namespace = key.indexOf(':');
        if (namespace >= 0 && namespace + 1 < key.length()) {
            key = key.substring(namespace + 1);
        }
        try {
            EntityType entityType = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            return entityType == EntityType.UNKNOWN ? null : entityType;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
        return parse(s, ValueKind.UNKNOWN, TimeUnitScale.SECONDS);
    }

    private static ParsedValue parse(String s, @NotNull ValueKind hint) {
        return parse(s, hint, TimeUnitScale.SECONDS);
    }

    














    private static ParsedValue parse(String s, @NotNull ValueKind hint, @NotNull TimeUnitScale bareTimeUnit) {
        if (hint != ValueKind.NUMBER) {
            Double colonSeconds = parseColon(s);
            if (colonSeconds != null) return new ParsedValue(colonSeconds, ValueKind.TIME);

            Long durationSeconds = parseDuration(s, hint);
            if (durationSeconds != null) return new ParsedValue(durationSeconds, ValueKind.TIME);

            if (hint == ValueKind.TIME) {


                ParsedValue number = parseNumber(s, false);
                return number == null ? null
                        : new ParsedValue(bareTimeUnit.toSeconds(number.value), ValueKind.TIME);
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

    public static boolean hasProviderRecursion(@NotNull String placeholder) {
        String lower = normalizePlaceholder(placeholder);
        return lower.contains("%bktops_") || lower.contains("% bktops_");
    }

    public static boolean isStatisticAggregator(@NotNull String placeholder) {
        StatisticQuery query = parseStatisticQuery(placeholder);
        return query != null && query.isAggregation();
    }

    private static @NotNull String normalizePlaceholder(@NotNull String placeholder) {
        return placeholder.trim().toLowerCase(Locale.ROOT);
    }

    private record StatisticQuery(Statistic statistic,
                                  Statistic.Type type,
                                  boolean aggregateAll,
                                  Material[] materials,
                                  EntityType[] entities) {

        private static @NotNull StatisticQuery aggregate(@NotNull Statistic statistic, @NotNull Statistic.Type type) {
            return new StatisticQuery(statistic, type, true, new Material[0], new EntityType[0]);
        }

        private static @NotNull StatisticQuery materialTargets(@NotNull Statistic statistic,
                                                                @NotNull Statistic.Type type,
                                                                Material @NotNull [] materials) {
            return new StatisticQuery(statistic, type, false, materials, new EntityType[0]);
        }

        private static @NotNull StatisticQuery entityTargets(@NotNull Statistic statistic,
                                                              EntityType @NotNull [] entities) {
            return new StatisticQuery(statistic, Statistic.Type.ENTITY, false, new Material[0], entities);
        }

        private boolean isAggregation() {
            return aggregateAll || materials.length > 1 || entities.length > 1;
        }
    }
}
