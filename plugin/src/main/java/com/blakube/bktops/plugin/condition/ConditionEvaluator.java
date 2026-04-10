package com.blakube.bktops.plugin.condition;

import com.blakube.bktops.api.storage.config.ConditionSet;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConditionEvaluator {

    private static final Map<String, ConditionExpression> EXPR_CACHE = new ConcurrentHashMap<>();

    private static final long MILLIS_PER_DAY = 86_400_000L;

    private ConditionEvaluator() {}

    public static boolean passes(@NotNull ConditionSet conditionSet, @NotNull UUID uuid) {
        if (conditionSet.isEmpty()) return true;

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        int inactivityDays = conditionSet.getInactivityDays();
        if (inactivityDays > 0) {
            long lastPlayed = player.getLastPlayed();
            if (lastPlayed > 0 && System.currentTimeMillis() - lastPlayed > inactivityDays * MILLIS_PER_DAY) {
                return false;
            }
        }

        List<String> rawExpressions = conditionSet.getRawExpressions();
        if (rawExpressions.isEmpty()) return true;

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return true;

        for (String raw : rawExpressions) {
            ConditionExpression expr = EXPR_CACHE.computeIfAbsent(raw, ConditionExpression::parse);
            if (expr == null) continue;
            if (!expr.evaluate(player)) return false;
        }

        return true;
    }

    public static void precompile(@NotNull List<String> rawExpressions) {
        for (String raw : rawExpressions) {
            EXPR_CACHE.computeIfAbsent(raw, ConditionExpression::parse);
        }
    }
}
