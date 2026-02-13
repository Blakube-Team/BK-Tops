package com.blakube.bktops.plugin;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.registry.TopRegistry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

public final class TopAPIImpl implements TopAPI {

    private final TopRegistry<java.util.UUID> registry;

    public TopAPIImpl(@NotNull TopRegistry<java.util.UUID> registry) {
        this.registry = registry;
    }

    @Override
    public @Nullable Top getTop(@NotNull String id) {
        return registry.get(id).orElse(null);
    }

    @Override
    public @NotNull Collection<Top> getAllTops() {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Collection<Top> tops = (Collection) registry.getAll();
        return tops;
    }

    @Override
    public Optional<Top> getTopByPlayer(@NotNull Player player) {
        java.util.UUID uuid = player.getUniqueId();
        for (Top<java.util.UUID> top : registry.getAll()) {
            if (top.isInTop(uuid)) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Top genericTop = top;
                return Optional.of(genericTop);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean restartTop(@NotNull String topId) {
        Optional<Top<java.util.UUID>> topOpt = registry.get(topId);
        if (topOpt.isEmpty()) return false;
        topOpt.get().reset();
        return true;
    }

    @Override
    public void registerTop(@NotNull Top top) {
        @SuppressWarnings({"unchecked"})
        Top<java.util.UUID> typed = (Top<java.util.UUID>) top;
        registry.register(typed);
    }

    @Override
    public void unregisterTop(@NotNull String topId) {
        registry.unregister(topId);
    }
}
