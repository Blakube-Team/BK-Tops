package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import org.kingdoms.constants.group.Kingdom;
import org.kingdoms.constants.player.KingdomPlayer;

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
        Kingdom kingdom = getKingdomOfPlayer(anyTeamMember);
        if(kingdom == null) return members;

        return kingdom.getMembers();
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        Kingdom kingdom = getKingdomOfPlayer(anyTeamMember);
        if(kingdom == null) return "";

        return kingdom.getName();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        return getKingdomOfPlayer(uuid) != null;
    }

    
    
    private Kingdom getKingdomOfPlayer(UUID playerId) {
        KingdomPlayer kingdomPlayer = KingdomPlayer.getKingdomPlayer(playerId);
        return kingdomPlayer == null ? null : kingdomPlayer.getKingdom();
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
