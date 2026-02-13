package com.blakube.bktops.plugin.resolver;

import com.blakube.bktops.plugin.hook.team.TeamHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.blakube.bktops.api.resolver.NameResolver;

import java.util.Objects;
import java.util.UUID;

public final class TeamNameResolver implements NameResolver<UUID> {

    private final TeamHandler teamHandler;
    private final PlayerNameResolver fallback;

    public TeamNameResolver(@NotNull TeamHandler teamHandler, @NotNull PlayerNameResolver fallback) {
        this.teamHandler = Objects.requireNonNull(teamHandler, "teamHandler");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public @Nullable String resolve(@NotNull UUID identifier) {
        return teamHandler.getTeamDisplayName(identifier).orElseGet(() -> fallback.resolve(identifier));
    }
}
