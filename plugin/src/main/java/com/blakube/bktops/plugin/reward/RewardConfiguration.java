package com.blakube.bktops.plugin.reward;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record RewardConfiguration(
        boolean enabled,
        boolean teamTop,
        @NotNull TeamRewardMode teamRewardMode,
        @NotNull List<RewardDefinition> definitions
) {

    public RewardConfiguration {
        definitions = List.copyOf(definitions);
    }

    @NotNull
    public static RewardConfiguration disabled(boolean teamTop) {
        return new RewardConfiguration(false, teamTop, TeamRewardMode.ENTRY, List.of());
    }
}
