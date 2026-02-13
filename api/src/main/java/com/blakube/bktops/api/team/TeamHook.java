package com.blakube.bktops.api.team;

import java.util.Set;
import java.util.UUID;

public interface TeamHook {
    String getPluginName();
    boolean isAvailable();
    int getPriority();

    Set<UUID> getTeamMembers(UUID anyTeamMember);
    String getTeamDisplayName(UUID anyTeamMember);

    boolean isTeamMember(UUID uuid);
    boolean validateTeamMembership(UUID playerId);

    Set<UUID> validateTeamMembers(Set<UUID> teamMembers);
}