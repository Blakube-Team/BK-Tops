package com.blakube.bktops.api.storage.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ConditionSet {

    public static final ConditionSet EMPTY = new ConditionSet(List.of(), 0);

    private final List<String> rawExpressions;
    private final int inactivityDays;

    public ConditionSet(@NotNull List<String> rawExpressions, int inactivityDays) {
        this.rawExpressions = List.copyOf(rawExpressions);
        this.inactivityDays = Math.max(0, inactivityDays);
    }

    @NotNull
    public List<String> getRawExpressions() {
        return rawExpressions;
    }

    public int getInactivityDays() {
        return inactivityDays;
    }

    public boolean isEmpty() {
        return rawExpressions.isEmpty() && inactivityDays <= 0;
    }
}
