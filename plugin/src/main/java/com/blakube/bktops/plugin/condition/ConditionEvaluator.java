package com.blakube.bktops.plugin.condition;

import com.blakube.bktops.api.storage.config.ConditionSet;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConditionEvaluator {

    private static final Map<String, ConditionExpression> EXPR_CACHE   = new ConcurrentHashMap<>();
    private static final Map<CacheKey, ResultEntry>       RESULT_CACHE = new ConcurrentHashMap<>();

    private static final long MILLIS_PER_DAY  = 86_400_000L;
    private static final long RESULT_TTL_MS   = 30_000L;
    private static final int  RESULT_CACHE_MAX = 10_000;

    private ConditionEvaluator() {}

    public static boolean passes(@NotNull ConditionSet conditionSet, @NotNull UUID uuid) {
        if (conditionSet.isEmpty()) return true;

        long now = System.currentTimeMillis();
        
        
        CacheKey key = new CacheKey(conditionSet, uuid);
        ResultEntry cached = RESULT_CACHE.get(key);
        if (cached != null && (now - cached.time) <= RESULT_TTL_MS) {
            return cached.passes;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        int inactivityDays = conditionSet.getInactivityDays();
        if (inactivityDays > 0) {
            long lastPlayed = player.getLastPlayed();
            if (lastPlayed > 0 && now - lastPlayed > (long) inactivityDays * MILLIS_PER_DAY) {
                cacheResult(key, false, now);
                return false;
            }
        }

        List<String> rawExpressions = conditionSet.getRawExpressions();
        if (rawExpressions.isEmpty()) {
            cacheResult(key, true, now);
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            cacheResult(key, true, now);
            return true;
        }

        for (String raw : rawExpressions) {
            ConditionExpression expr = EXPR_CACHE.computeIfAbsent(raw, ConditionExpression::parse);
            if (expr == null) continue;
            if (!expr.evaluate(player)) {
                cacheResult(key, false, now);
                return false;
            }
        }

        cacheResult(key, true, now);
        return true;
    }

    private static void cacheResult(CacheKey key, boolean passes, long now) {
        if (RESULT_CACHE.size() >= RESULT_CACHE_MAX) {
            RESULT_CACHE.entrySet().removeIf(e -> (now - e.getValue().time) > RESULT_TTL_MS);
        }
        RESULT_CACHE.put(key, new ResultEntry(passes, now));
    }

    private record CacheKey(ConditionSet conditionSet, UUID uuid) {}

    public static void precompile(@NotNull List<String> rawExpressions) {
        for (String raw : rawExpressions) {
            EXPR_CACHE.computeIfAbsent(raw, ConditionExpression::parse);
        }
    }

    private static final class ResultEntry {
        final boolean passes;
        final long    time;
        ResultEntry(boolean passes, long time) { this.passes = passes; this.time = time; }
    }
}
