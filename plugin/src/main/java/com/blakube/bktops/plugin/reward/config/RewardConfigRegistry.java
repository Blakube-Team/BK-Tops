package com.blakube.bktops.plugin.reward.config;

import com.blakube.bktops.plugin.reward.RewardConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RewardConfigRegistry {

    private final Map<String, RewardConfiguration> rewardsByTop = new ConcurrentHashMap<>();

    public void set(@NotNull String topId, @NotNull RewardConfiguration configuration) {
        rewardsByTop.put(topId, configuration);
    }

    @NotNull
    public Optional<RewardConfiguration> get(@NotNull String topId) {
        return Optional.ofNullable(rewardsByTop.get(topId));
    }

    public void clear() {
        rewardsByTop.clear();
    }
}
