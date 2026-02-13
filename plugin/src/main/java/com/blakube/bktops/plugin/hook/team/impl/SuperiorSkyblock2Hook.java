package com.blakube.bktops.plugin.hook.team.impl;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.blakube.bktops.api.team.TeamHook;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SuperiorSkyblock2Hook implements TeamHook {

    private final boolean available;
    private final int priority;

    public SuperiorSkyblock2Hook(TeamHookHelpService helper) {
        this.available = helper.resolveAvailability("superior-skyblock-2", "SuperiorSkyblock2");
        this.priority = helper.getPriority("superior-skyblock-2");
    }

    @Override
    public String getPluginName() {
        return "SuperiorSkyblock2";
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
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(anyTeamMember);
        if (superiorPlayer == null) return Set.of();

        Island island = superiorPlayer.getIsland();
        if (island == null) return Set.of();

        return island.getIslandMembers(true).stream()
                .map(SuperiorPlayer::getUniqueId)
                .collect(Collectors.toSet());
    }

    @Override
    public String getTeamDisplayName(UUID anyTeamMember) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(anyTeamMember);
        if (superiorPlayer == null) return "";

        Island island = superiorPlayer.getIsland();
        if (island == null) return "";

        String name = island.getName();
        return name != null ? name : "";
    }

    @Override
    public boolean isTeamMember(UUID uuid) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(uuid);
        if (superiorPlayer == null) return false;

        return superiorPlayer.getIsland() != null;
    }

    @Override
    public boolean validateTeamMembership(UUID playerId) {
        return isTeamMember(playerId);
    }

    @Override
    public Set<UUID> validateTeamMembers(Set<UUID> teamMembers) {
        return teamMembers.stream()
                .filter(this::isTeamMember)
                .collect(Collectors.toSet());
    }
}
