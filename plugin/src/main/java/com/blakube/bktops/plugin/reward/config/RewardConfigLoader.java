package com.blakube.bktops.plugin.reward.config;

import com.blakube.bktops.api.config.ConfigContainer;
import com.blakube.bktops.plugin.reward.PhysicalReward;
import com.blakube.bktops.plugin.reward.RewardConfiguration;
import com.blakube.bktops.plugin.reward.RewardDefinition;
import com.blakube.bktops.plugin.reward.RewardPositionRange;
import com.blakube.bktops.plugin.reward.TeamRewardMode;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public final class RewardConfigLoader {

    private final ConfigContainer topsConfig;
    private final Logger logger;

    public RewardConfigLoader(@NotNull ConfigContainer topsConfig, @NotNull Logger logger) {
        this.topsConfig = topsConfig;
        this.logger = logger;
    }

    public void loadInto(@NotNull RewardConfigRegistry registry) {
        registry.clear();

        Set<String> topIds = topsConfig.getKeys("");
        for (String topId : topIds) {
            registry.set(topId, loadTopRewards(topId));
        }
    }

    @NotNull
    private RewardConfiguration loadTopRewards(@NotNull String topId) {
        String type = topsConfig.getString(topId + ".type", "normal");
        boolean teamTop = type.equalsIgnoreCase("team") || type.equalsIgnoreCase("team-timed");

        ConfigurationSection rewards = topsConfig.getConfigurationSection(topId + ".rewards");
        if (rewards == null) {
            return RewardConfiguration.disabled(teamTop);
        }

        boolean enabled = rewards.getBoolean("enabled", true);
        TeamRewardMode defaultTeamMode = TeamRewardMode.fromConfig(
                rewards.getString("team-reward-mode"),
                TeamRewardMode.ENTRY
        );

        ConfigurationSection positions = rewards.getConfigurationSection("positions");
        if (positions == null) {
            return new RewardConfiguration(enabled, teamTop, defaultTeamMode, List.of());
        }

        List<RewardDefinition> definitions = new ArrayList<>();
        for (String key : positions.getKeys(false)) {
            ConfigurationSection section = positions.getConfigurationSection(key);
            if (section == null) continue;

            try {
                RewardPositionRange range = RewardPositionRange.parse(key);
                TeamRewardMode mode = TeamRewardMode.fromConfig(section.getString("team-reward-mode"), defaultTeamMode);
                List<PhysicalReward> items = parseItems(section.getConfigurationSection("items"));
                List<String> commands = section.getStringList("commands");

                RewardDefinition definition = new RewardDefinition(key, range, mode, items, commands);
                if (definition.hasActions()) {
                    definitions.add(definition);
                }
            } catch (RuntimeException ex) {
                logger.warning("Invalid reward definition '" + key + "' for top '" + topId + "': " + ex.getMessage());
            }
        }

        return new RewardConfiguration(enabled, teamTop, defaultTeamMode, definitions);
    }

    @NotNull
    private List<PhysicalReward> parseItems(ConfigurationSection itemsSection) {
        if (itemsSection == null) return List.of();

        List<PhysicalReward> items = new ArrayList<>();
        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
            if (itemSection == null) continue;

            String itemData = itemSection.getString("item");
            int amount = itemSection.getInt("amount", 1);
            if (itemData == null || itemData.isBlank() || amount <= 0) continue;

            items.add(new PhysicalReward(itemData, amount));
        }
        return items;
    }
}
