package com.blakube.bktops.plugin.reward.listener;

import com.blakube.bktops.api.event.top.TimedTopResetEvent;
import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.cache.PlayerNameCache;
import com.blakube.bktops.plugin.hook.team.TeamHandler;
import com.blakube.bktops.plugin.reward.PendingReward;
import com.blakube.bktops.plugin.reward.PendingRewardService;
import com.blakube.bktops.plugin.reward.PhysicalReward;
import com.blakube.bktops.plugin.reward.RewardActionType;
import com.blakube.bktops.plugin.reward.RewardConfiguration;
import com.blakube.bktops.plugin.reward.RewardDefinition;
import com.blakube.bktops.plugin.reward.TeamRewardMode;
import com.blakube.bktops.plugin.reward.config.RewardConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TopRewardListener implements Listener {

    private final RewardConfigRegistry rewardConfigs;
    private final PendingRewardService pendingRewards;
    private final TeamHandler teamHandler;

    public TopRewardListener(@NotNull RewardConfigRegistry rewardConfigs,
                             @NotNull PendingRewardService pendingRewards,
                             @NotNull TeamHandler teamHandler) {
        this.rewardConfigs = rewardConfigs;
        this.pendingRewards = pendingRewards;
        this.teamHandler = teamHandler;
    }

    @EventHandler
    public void onTimedTopReset(@NotNull TimedTopResetEvent event) {
        Optional<RewardConfiguration> configOpt = rewardConfigs.get(event.getTopId());
        if (configOpt.isEmpty()) return;

        RewardConfiguration config = configOpt.get();
        if (!config.enabled() || config.definitions().isEmpty()) return;

        String batchId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        List<PendingReward> pending = new ArrayList<>();

        for (TopEntry<?> entry : event.getPreviousEntries()) {
            if (!(entry.getIdentifier() instanceof UUID entryUuid)) continue;

            for (RewardDefinition definition : config.definitions()) {
                if (!definition.range().matches(entry.getPosition())) continue;

                Set<UUID> recipients = resolveRecipients(config, definition, entryUuid);
                for (UUID recipient : recipients) {
                    String playerName = resolveName(recipient, entry);
                    for (PhysicalReward item : definition.items()) {
                        pending.add(new PendingReward(
                                UUID.randomUUID().toString(),
                                batchId,
                                event.getTopId(),
                                recipient,
                                playerName,
                                entry.getPosition(),
                                entry.getValue(),
                                RewardActionType.ITEM,
                                item.itemData(),
                                item.amount(),
                                createdAt
                        ));
                    }
                    for (String command : definition.commands()) {
                        if (command == null || command.isBlank()) continue;
                        pending.add(new PendingReward(
                                UUID.randomUUID().toString(),
                                batchId,
                                event.getTopId(),
                                recipient,
                                playerName,
                                entry.getPosition(),
                                entry.getValue(),
                                RewardActionType.COMMAND,
                                command,
                                1,
                                createdAt
                        ));
                    }
                }
            }
        }

        pendingRewards.enqueue(pending);
    }

    @NotNull
    private Set<UUID> resolveRecipients(@NotNull RewardConfiguration config,
                                        @NotNull RewardDefinition definition,
                                        @NotNull UUID entryUuid) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (config.teamTop() && definition.teamRewardMode() == TeamRewardMode.MEMBERS) {
            teamHandler.getTeamMembers(entryUuid).ifPresent(recipients::addAll);
        }

        if (recipients.isEmpty()) {
            recipients.add(entryUuid);
        }
        return recipients;
    }

    private String resolveName(@NotNull UUID uuid, @NotNull TopEntry<?> entry) {
        if (uuid.equals(entry.getIdentifier())) {
            return entry.getDisplayName();
        }

        String cached = PlayerNameCache.get(uuid);
        if (cached != null) return cached;

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return offlinePlayer.getName();
    }
}
