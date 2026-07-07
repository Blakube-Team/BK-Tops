package com.blakube.bktops.plugin.reward;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record RewardDefinition(
        @NotNull String positionKey,
        @NotNull RewardPositionRange range,
        @NotNull TeamRewardMode teamRewardMode,
        @NotNull List<PhysicalReward> items,
        @NotNull List<String> commands
) {

    public RewardDefinition {
        items = List.copyOf(items);
        commands = List.copyOf(commands);
    }

    public boolean hasActions() {
        return !items.isEmpty() || !commands.isEmpty();
    }
}
