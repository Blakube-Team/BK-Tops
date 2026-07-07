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
        if (available) {
            this.api = LandsIntegration.of(Bukkit.getPluginManager().getPlugin("BK-Tops"));
        }
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
        var land = resolveLand(anyTeamMember);
        if (land == null) return Set.of();
        var nation = land.getNation();
        if (nation != null) {
            return nation.getTrustedPlayers().stream().collect(Collectors.toSet());
        }
        return land.getTrustedPlayers().stream().collect(Collectors.toSet());
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        var land = resolveLand(anyTeamMember);
        if (land == null) return null;
        return land.getName();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        return resolveLand(uuid) != null;
    }

    
    
    private me.angeschossen.lands.api.land.Land resolveLand(UUID playerId) {
        LandPlayer landPlayer = api.getLandPlayer(playerId);
        if (landPlayer == null) return null;
        var owningLand = landPlayer.getOwningLand();
        if (owningLand != null) return owningLand;
        var lands = landPlayer.getLands();
        if (lands == null || lands.isEmpty()) return null;
        return lands.iterator().next();
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
