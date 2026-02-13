package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import me.ulrich.clans.api.ClanAPIManager;
import me.ulrich.clans.api.PlayerAPIManager;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class TownyHook implements TeamHook {

    private final boolean available;
    private final int priority;

    public TownyHook(TeamHookHelpService helper) {
        this.available = helper.resolveAvailability("towny", "Towny");
        this.priority = helper.getPriority("towny");
    }

    @Override
    public String getPluginName() {
        return "Towny";
    }

    @Override
    public boolean isAvailable() { return available; }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Set<UUID> getTeamMembers(UUID anyTeamMember) {
        Set<UUID> members = Set.of();
        Town town = TownyAPI.getInstance().getTown(anyTeamMember);
        if(town == null) return members;

        return town.getResidents().stream().map(Resident::getUUID).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        Town town = TownyAPI.getInstance().getTown(anyTeamMember);
        if(town == null) return "";

        return town.getName();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        Town town = TownyAPI.getInstance().getTown(uuid);
        return town != null;
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
