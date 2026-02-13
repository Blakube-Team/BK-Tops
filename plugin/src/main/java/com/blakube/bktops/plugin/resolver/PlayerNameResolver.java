package com.blakube.bktops.plugin.resolver;

import com.blakube.bktops.api.resolver.NameResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PlayerNameResolver implements NameResolver<UUID> {

    @Override
    @Nullable
    public String resolve(@NotNull UUID identifier) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(identifier);
        return player.getName();
    }
}