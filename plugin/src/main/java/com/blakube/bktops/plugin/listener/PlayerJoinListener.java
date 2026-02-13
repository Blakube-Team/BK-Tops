package com.blakube.bktops.plugin.listener;

import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.top.Top;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final TopRegistry<UUID> registry;

    public PlayerJoinListener(TopRegistry<UUID> registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        for (Top<UUID> top : registry.getAll()) {
            top.enqueue(List.of(uuid), Priority.CRITICAL, "player_join");
        }
    }
}
