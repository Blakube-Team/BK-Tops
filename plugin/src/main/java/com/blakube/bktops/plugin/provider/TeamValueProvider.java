package com.blakube.bktops.plugin.provider;

import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.plugin.service.team.TeamScoreService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TeamValueProvider implements ValueProvider<UUID> {

    private final TeamScoreService teamScoreService;
    private final String placeholder;
    private final Plugin plugin;

    public TeamValueProvider(@NotNull Plugin plugin,
                             @NotNull TeamScoreService teamScoreService,
                             @NotNull String placeholder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teamScoreService = Objects.requireNonNull(teamScoreService, "teamScoreService");
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
    }

    @Override
    public @Nullable Double getValue(@NotNull UUID identifier) {
        Optional<Double> sum = teamScoreService.computeTeamScore(identifier, placeholder);
        return sum.orElse(null);
    }

    @Override
    public @NotNull String getName() {
        return "TeamSum[" + placeholder + "]";
    }

    @Override
    public boolean isAvailable() {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        return papi != null && papi.isEnabled();
    }
}
