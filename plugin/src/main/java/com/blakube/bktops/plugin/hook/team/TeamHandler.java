package com.blakube.bktops.plugin.hook.team;

import com.blakube.bktops.api.team.TeamHook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class TeamHandler {

    private final List<TeamHook> hooks = new CopyOnWriteArrayList<>();

    public void initAllHooks(@NotNull com.blakube.bktops.plugin.service.team.TeamHookHelpService helper) {
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.LandsHook(helper));
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.SuperiorSkyblock2Hook(helper));
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.BentoBoxHook(helper));
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.UltimateClansHook(helper));
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.KingdomsXHook(helper));
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.TownyHook(helper));
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.FactionsUUIDHook(helper));
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.BetterTeamsHook(helper));
        registerIfAvailable(new com.blakube.bktops.plugin.hook.team.impl.SimpleClansHook(helper));
    }

    private void registerIfAvailable(@NotNull TeamHook hook) {
        if (hook.isAvailable()) {
            registerHook(hook);
        }
    }

    public void registerHook(@NotNull TeamHook hook) {
        hooks.add(hook);
        sortHooks();
    }

    public void unregisterHook(@NotNull TeamHook hook) {
        hooks.remove(hook);
    }

    public @NotNull List<TeamHook> getRegisteredHooks() {
        return List.copyOf(hooks);
    }

    public @NotNull Optional<TeamHook> getPrimaryHook() {
        return hooks.stream()
                .filter(Objects::nonNull)
                .filter(TeamHook::isAvailable)
                .findFirst();
    }

    private @NotNull Optional<TeamHook> resolveHookFor(@NotNull UUID playerId) {
        return hooks.stream()
                .filter(Objects::nonNull)
                .filter(TeamHook::isAvailable)
                .filter(h -> safeIsTeamMember(h, playerId))
                .findFirst();
    }

    public boolean isTeamMember(@NotNull UUID playerId) {
        return resolveHookFor(playerId).isPresent();
    }

    public boolean validateTeamMembership(@NotNull UUID playerId) {
        return resolveHookFor(playerId)
                .map(h -> safeValidateTeamMembership(h, playerId))
                .orElse(false);
    }

    public @NotNull Set<UUID> validateTeamMembers(@NotNull Set<UUID> teamMembers) {
        if (teamMembers.isEmpty()) return Collections.emptySet();

        Optional<TeamHook> hookOpt = teamMembers.stream()
                .map(this::resolveHookFor)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (hookOpt.isEmpty()) return Collections.emptySet();
        TeamHook hook = hookOpt.get();
        try {
            return hook.validateTeamMembers(teamMembers);
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    public @NotNull Optional<Set<UUID>> getTeamMembers(@NotNull UUID anyTeamMember) {
        return resolveHookFor(anyTeamMember)
                .map(h -> safeGetTeamMembers(h, anyTeamMember))
                .map(members -> members == null ? Collections.<UUID>emptySet() : members)
                .map(Collections::unmodifiableSet);
    }

    public @NotNull Optional<String> getTeamDisplayName(@NotNull UUID anyTeamMember) {
        return resolveHookFor(anyTeamMember)
                .map(h -> safeGetTeamDisplayName(h, anyTeamMember))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    private boolean safeIsTeamMember(@NotNull TeamHook hook, @NotNull UUID playerId) {
        try { return hook.isTeamMember(playerId); } catch (Throwable t) { return false; }
    }

    private boolean safeValidateTeamMembership(@NotNull TeamHook hook, @NotNull UUID playerId) {
        try { return hook.validateTeamMembership(playerId); } catch (Throwable t) { return false; }
    }

    private @Nullable Set<UUID> safeGetTeamMembers(@NotNull TeamHook hook, @NotNull UUID playerId) {
        try { return hook.getTeamMembers(playerId); } catch (Throwable t) { return null; }
    }

    private @Nullable String safeGetTeamDisplayName(@NotNull TeamHook hook, @NotNull UUID playerId) {
        try { return hook.getTeamDisplayName(playerId); } catch (Throwable t) { return null; }
    }

    private void sortHooks() {
        hooks.sort(Comparator.comparingInt(TeamHook::getPriority).reversed());
    }

    @Override
    public String toString() {
        return "TeamHandler{" +
                "hooks=" + hooks.stream()
                .map(h -> h.getPluginName() + "(p=" + h.getPriority() + ",avail=" + h.isAvailable() + ")")
                .collect(Collectors.joining(", ")) +
                '}';
    }
}
