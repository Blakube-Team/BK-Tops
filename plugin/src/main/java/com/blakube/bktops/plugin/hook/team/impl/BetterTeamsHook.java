package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BetterTeamsHook implements TeamHook {

    private final boolean available;
    private final int priority;

    public BetterTeamsHook(TeamHookHelpService helper) {
        this.available = helper.resolveAvailability("better-teams", "BetterTeams");
        this.priority = helper.getPriority("better-teams");
    }

    @Override
    public String getPluginName() {
        return "BetterTeams";
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
        Set<UUID> members = Set.of();
        Team team = Team.getTeam(anyTeamMember);
        if(team == null) return members;

        return team.getMembers().get().stream().map(TeamPlayer::getPlayerUUID).collect(Collectors.toSet());
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        Team team = Team.getTeam(anyTeamMember);
        if(team == null) return null;

        return team.getDisplayName();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        return Team.getTeam(uuid) != null;
    }

    @Override
    public boolean validateTeamMembership(UUID playerId) {
        return isTeamMember(playerId);
    }

    @Override
    public Set<UUID> validateTeamMembers(Set<UUID> teamMembers) {
        return teamMembers.stream().filter(this::isTeamMember).collect(Collectors.toSet());
    }
}
