package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import org.kingdoms.constants.group.Kingdom;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class KingdomsXHook implements TeamHook {

    private final boolean available;
    private final int priority;

    public KingdomsXHook(TeamHookHelpService helper) {
        this.available = helper.resolveAvailability("kingdoms-x", "KingdomsX", "Kingdoms");
        this.priority = helper.getPriority("kingdoms-x");
    }

    @Override
    public String getPluginName() {
        return "KingdomsX";
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
        Kingdom kingdom = Kingdom.getKingdom(anyTeamMember);
        if(kingdom == null) return members;

        return kingdom.getMembers();
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        Kingdom kingdom = Kingdom.getKingdom(anyTeamMember);
        if(kingdom == null) return "";

        return kingdom.getName();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        return Kingdom.getKingdom(uuid) != null;
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
