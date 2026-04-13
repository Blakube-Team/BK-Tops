package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class FactionsUUIDHook implements TeamHook {

    private final boolean isAvailable;
    private final int priority;

    public FactionsUUIDHook(TeamHookHelpService teamHookHelpService) {
        this.isAvailable = teamHookHelpService.resolveAvailability(
                "factions-uuid",
                "FactionsUUID", "Factions"
        );
        this.priority = teamHookHelpService.getPriority("factions-uuid");
    }

    @Override
    public String getPluginName() {
        return "FactionsUUID";
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Set<UUID> getTeamMembers(UUID anyTeamMember) {
        FPlayer fPlayer = FPlayers.fPlayers().get(anyTeamMember);
        if (fPlayer == null || !fPlayer.hasFaction()) return Set.of();

        return fPlayer.faction().members().stream()
                .map(FPlayer::asPlayer)
                .filter(Objects::nonNull)
                .map(p -> p.getUniqueId())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        FPlayer fPlayer = FPlayers.fPlayers().get(anyTeamMember);
        if (fPlayer == null || !fPlayer.hasFaction()) return "";
        return fPlayer.faction().tag();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        FPlayer fPlayer = FPlayers.fPlayers().get(uuid);
        return fPlayer != null && fPlayer.hasFaction();
    }

    @Override
    public boolean validateTeamMembership(UUID playerId) {
        return isTeamMember(playerId);
    }

    @Override
    public Set<UUID> validateTeamMembers(Set<UUID> teamMembers) {
        return teamMembers.stream().filter(this::isTeamMember).collect(java.util.stream.Collectors.toSet());
    }
}
