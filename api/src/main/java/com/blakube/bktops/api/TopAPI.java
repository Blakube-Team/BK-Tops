package com.blakube.bktops.api;

import com.blakube.bktops.api.top.Top;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

public interface TopAPI {

    @Nullable
    Top getTop(@NotNull String id);

    @NotNull
    Collection<Top> getAllTops();

    Optional<Top> getTopByPlayer(@NotNull Player player);

    boolean restartTop(@NotNull String topId);
    void registerTop(@NotNull Top topId);
    void unregisterTop(@NotNull String topId);

}