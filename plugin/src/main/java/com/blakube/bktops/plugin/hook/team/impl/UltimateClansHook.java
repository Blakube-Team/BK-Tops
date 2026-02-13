package com.blakube.bktops.plugin.hook.team.impl;

import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import me.ulrich.clans.api.ClanAPIManager;
import me.ulrich.clans.api.PlayerAPIManager;
import me.ulrich.clans.data.ClanData;
import me.ulrich.clans.interfaces.UClans;
import org.bukkit.Bukkit;

import java.util.*;

public final class UltimateClansHook implements TeamHook {

    private final boolean available;
    private final int priority;
    private ClanAPIManager clanAPI;
    private PlayerAPIManager playerAPI;

    public UltimateClansHook(TeamHookHelpService helper) {
        this.available = helper.resolveAvailability("ultimate-clans", "UltimateClans");
        this.priority = helper.getPriority("ultimate-clans");

        if (Bukkit.getPluginManager().isPluginEnabled("UltimateClans")) {
            UClans clansAPI = (UClans) Bukkit.getPluginManager().getPlugin("UltimateClans");
            clanAPI = clansAPI.getClanAPI();
            playerAPI = clansAPI.getPlayerAPI();
        }
    }

    @Override
    public String getPluginName() {
        return "UltimateClans";
    }

    @Override
    public boolean isAvailable() { return available; }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Set<UUID> getTeamMembers(UUID anyTeamMember) {
        Set<UUID> members = new HashSet<>();
        playerAPI.getPlayerClan(anyTeamMember).ifPresent(clanData -> {
            members.addAll(clanData.getMembers());
        });
        return members;
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        Optional<ClanData> clanOpt = playerAPI.getPlayerClan(anyTeamMember);
        if(clanOpt.isPresent()) {
            return clanOpt.get().getTag();
        }
        return "";
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        Optional<ClanData> clanOpt = playerAPI.getPlayerClan(uuid);
        return clanOpt.isPresent();
    }

    @Override
    public boolean validateTeamMembership(UUID playerId) {
        return playerAPI.getPlayerClan(playerId).isPresent();
    }

    @Override
    public Set<UUID> validateTeamMembers(Set<UUID> teamMembers) {
        Set<UUID> validMembers = new HashSet<>();
        for (UUID memberId : teamMembers) {
            if (validateTeamMembership(memberId)) {
                validMembers.add(memberId);
            }
        }
        return validMembers;
    }
}
