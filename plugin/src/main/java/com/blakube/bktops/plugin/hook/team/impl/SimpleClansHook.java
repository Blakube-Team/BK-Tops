package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class SimpleClansHook implements TeamHook {

    private final boolean available;
    private final int priority;
    private SimpleClans simpleClansAPI;

    public SimpleClansHook(TeamHookHelpService helper) {
        this.available = helper.resolveAvailability("simple-clans", "SimpleClans");
        this.priority = helper.getPriority("simple-clans");
        simpleClansAPI = SimpleClans.getInstance();
    }

    @Override
    public String getPluginName() {
        return "SimpleClans";
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
        var clan = getClan(anyTeamMember);
        if(clan == null) return members;

        return clan.getMembers().stream()
                .map(ClanPlayer::getUniqueId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        var clan = getClan(anyTeamMember);
        if(clan == null) return null;
        return clan.getName();
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        var clanPlayer = simpleClansAPI.getClanManager().getClanPlayer(uuid);
        return clanPlayer != null && clanPlayer.getClan() != null;
    }

    @Override
    public boolean validateTeamMembership(UUID playerId) {
        return isTeamMember(playerId);
    }

    @Override
    public Set<UUID> validateTeamMembers(Set<UUID> teamMembers) {
        return teamMembers.stream().filter(this::isTeamMember).collect(java.util.stream.Collectors.toSet());
    }

    private Clan getClan(UUID anyTeamMember) {
        var clanPlayer = simpleClansAPI.getClanManager().getClanPlayer(anyTeamMember);
        if(clanPlayer == null) return null;
        return clanPlayer.getClan();
    }
}
