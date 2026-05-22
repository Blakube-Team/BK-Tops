package com.blakube.bktops.plugin.listener;

import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.plugin.cache.PlayerNameCache;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final TopRegistry<UUID> registry;

    public PlayerQuitListener(TopRegistry<UUID> registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerNameCache.put(uuid, player.getName());

        
        
        
        for (Top<UUID> top : registry.getAll()) {
            top.getProcessor().processImmediate(uuid, "player_quit");
        }
    }
}
