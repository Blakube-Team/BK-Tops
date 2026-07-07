package com.blakube.bktops.plugin.listener;

import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.plugin.cache.PlayerNameCache;
import com.blakube.bktops.plugin.reward.PendingRewardService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final TopRegistry<UUID> registry;
    private final PendingRewardService pendingRewardService;

    public PlayerJoinListener(TopRegistry<UUID> registry, @Nullable PendingRewardService pendingRewardService) {
        this.registry = registry;
        this.pendingRewardService = pendingRewardService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerNameCache.put(uuid, player.getName());

        for (Top<UUID> top : registry.getAll()) {
            top.enqueue(List.of(uuid), Priority.HIGH, "player_join");
        }

        if (pendingRewardService != null) {
            pendingRewardService.deliverPending(player);
        }
    }
}
