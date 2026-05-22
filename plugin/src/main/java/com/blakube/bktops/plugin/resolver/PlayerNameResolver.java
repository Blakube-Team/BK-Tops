package com.blakube.bktops.plugin.resolver;

import com.blakube.bktops.api.resolver.NameResolver;
import com.blakube.bktops.plugin.cache.PlayerNameCache;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PlayerNameResolver implements NameResolver<UUID> {

    @Override
    @Nullable
    public String resolve(@NotNull UUID identifier) {
        String cached = PlayerNameCache.get(identifier);
        if (cached != null) return cached;

        
        if (!Bukkit.isPrimaryThread()) return null;

        OfflinePlayer player = Bukkit.getOfflinePlayer(identifier);
        String name = player.getName();
        if (name != null) PlayerNameCache.put(identifier, name);
        return name;
    }
}
