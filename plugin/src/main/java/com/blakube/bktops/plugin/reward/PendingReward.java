package com.blakube.bktops.plugin.reward;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PendingReward(
        @NotNull String id,
        @NotNull String batchId,
        @NotNull String topId,
        @NotNull UUID playerUuid,
        @Nullable String playerName,
        int position,
        double score,
        @NotNull RewardActionType actionType,
        @NotNull String payload,
        int amount,
        long createdAt
) {
}
