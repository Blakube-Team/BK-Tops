package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.Bukkit;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class LandsHook implements TeamHook {

    private final boolean available;
    private final int priority;
    private LandsIntegration api;

    public LandsHook(TeamHookHelpService helper) {
        this.available = helper.resolveAvailability("lands", "Lands");
        this.priority = helper.getPriority("lands");
        this.api = LandsIntegration.of(Bukkit.getPluginManager().getPlugin("BK-Tops"));
    }

    @Override
    public String getPluginName() {
        return "Lands";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Set<UUID> getTeamMembers(UUID anyTeamMember) {
        LandPlayer landPlayer = api.getLandPlayer(anyTeamMember);
        if (landPlayer == null) return Set.of();
        var owningLand = landPlayer.getOwningLand();
        if (owningLand == null) return Set.of();
        var nation = owningLand.getNation();
        if (nation != null) {
            return nation.getTrustedPlayers().stream().collect(Collectors.toSet());
        }
        return owningLand.getTrustedPlayers().stream().collect(Collectors.toSet());
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        LandPlayer landPlayer = api.getLandPlayer(anyTeamMember);
        if (landPlayer == null) return null;
        var owningLand = landPlayer.getOwningLand();
        if (owningLand == null) return null;
        return owningLand.getName();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        LandPlayer landPlayer = api.getLandPlayer(uuid);
        return landPlayer != null && landPlayer.getOwningLand() != null;
    }

    @Override
    public boolean validateTeamMembership(UUID playerId) {
        return isTeamMember(playerId);
    }

    @Override
    public Set<UUID> validateTeamMembers(Set<UUID> teamMembers) {
        return  teamMembers.stream().filter(this::isTeamMember).collect(Collectors.toSet());
    }
}
