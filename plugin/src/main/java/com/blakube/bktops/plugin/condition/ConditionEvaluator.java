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
    private static final Map<UUID,   ResultEntry>         RESULT_CACHE = new ConcurrentHashMap<>();

    private static final long MILLIS_PER_DAY  = 86_400_000L;
    private static final long RESULT_TTL_MS   = 30_000L; 

    private ConditionEvaluator() {}

    public static boolean passes(@NotNull ConditionSet conditionSet, @NotNull UUID uuid) {
        if (conditionSet.isEmpty()) return true;

        long now = System.currentTimeMillis();
        ResultEntry cached = RESULT_CACHE.get(uuid);
        if (cached != null && (now - cached.time) <= RESULT_TTL_MS) {
            return cached.passes;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        int inactivityDays = conditionSet.getInactivityDays();
        if (inactivityDays > 0) {
            long lastPlayed = player.getLastPlayed();
            if (lastPlayed > 0 && now - lastPlayed > (long) inactivityDays * MILLIS_PER_DAY) {
                RESULT_CACHE.put(uuid, new ResultEntry(false, now));
                return false;
            }
        }

        List<String> rawExpressions = conditionSet.getRawExpressions();
        if (rawExpressions.isEmpty()) {
            RESULT_CACHE.put(uuid, new ResultEntry(true, now));
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            RESULT_CACHE.put(uuid, new ResultEntry(true, now));
            return true;
        }

        for (String raw : rawExpressions) {
            ConditionExpression expr = EXPR_CACHE.computeIfAbsent(raw, ConditionExpression::parse);
            if (expr == null) continue;
            if (!expr.evaluate(player)) {
                RESULT_CACHE.put(uuid, new ResultEntry(false, now));
                return false;
            }
        }

        RESULT_CACHE.put(uuid, new ResultEntry(true, now));
        return true;
    }

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
