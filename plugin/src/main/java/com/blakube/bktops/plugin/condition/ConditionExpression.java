package com.blakube.bktops.plugin.condition;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConditionExpression {

    private static final Pattern EXPR_PATTERN =
            Pattern.compile("^(\\S+)\\s*(==|!=|>=|<=|>|<)\\s*(\\S+)$");

    enum Operator { EQ, NEQ, GTE, LTE, GT, LT }

    private final String placeholder;
    private final Operator operator;
    private final String targetValue;

    private ConditionExpression(String placeholder, Operator operator, String targetValue) {
        this.placeholder = placeholder;
        this.operator    = operator;
        this.targetValue = targetValue;
    }

    @Nullable
    public static ConditionExpression parse(@NotNull String raw) {
        Matcher m = EXPR_PATTERN.matcher(raw.trim());
        if (!m.matches()) return null;

        Operator op = switch (m.group(2)) {
            case "==" -> Operator.EQ;
            case "!=" -> Operator.NEQ;
            case ">=" -> Operator.GTE;
            case "<=" -> Operator.LTE;
            case ">"  -> Operator.GT;
            case "<"  -> Operator.LT;
            default   -> null;
        };
        if (op == null) return null;

        return new ConditionExpression(m.group(1), op, m.group(3));
    }

    public boolean evaluate(@NotNull OfflinePlayer player) {
        String raw = PlaceholderAPI.setPlaceholders(player, placeholder);
        if (raw == null || raw.isBlank()) return true;

        String actual = raw.trim();
        String target = targetValue.trim();

        try {
            double a = Double.parseDouble(actual);
            double t = Double.parseDouble(target);
            return switch (operator) {
                case EQ  -> a == t;
                case NEQ -> a != t;
                case GTE -> a >= t;
                case LTE -> a <= t;
                case GT  -> a >  t;
                case LT  -> a <  t;
            };
        } catch (NumberFormatException ignored) {}

        return switch (operator) {
            case EQ  ->  actual.equalsIgnoreCase(target);
            case NEQ -> !actual.equalsIgnoreCase(target);
            default  -> true;
        };
    }

    @Override
    public String toString() {
        return placeholder + " " + operator.name() + " " + targetValue;
    }
}
