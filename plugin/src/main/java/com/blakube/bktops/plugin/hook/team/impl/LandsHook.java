package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import me.angeschossen.lands.api.LandsIntegration;
import org.bukkit.Bukkit;

import java.util.Collections;
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
        var land = api.getLandPlayer(anyTeamMember).getOwningLand();
        if(land != null) return land.getNation().getTrustedPlayers().stream().collect(Collectors.toSet());
        return null;
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        var land = api.getLandPlayer(anyTeamMember);
        land.getOwningLand().getName();
        return land.getName();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        var land = api.getLandPlayer(uuid);
        return land != null;
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
