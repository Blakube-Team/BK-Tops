package com.blakube.bktops.plugin.service.team;

import com.blakube.bktops.plugin.hook.team.TeamHandler;
import com.blakube.bktops.plugin.provider.PlaceholderValueProvider;
import com.blakube.bktops.plugin.provider.ValueKind;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class TeamScoreService {

    private final Plugin plugin;
    private final TeamHandler teamHandler;
    private final ValueKind parseHint;
    private final Map<String, PlaceholderValueProvider> providers = new ConcurrentHashMap<>();
    
    private static final long MEMBERS_TTL_MILLIS = TimeUnit.SECONDS.toMillis(3);
    private static final long NAME_TTL_MILLIS = TimeUnit.SECONDS.toMillis(3);
    private final Map<UUID, TimedEntry<Set<UUID>>> membersCache = new ConcurrentHashMap<>();
    private final Map<UUID, TimedEntry<String>> nameCache = new ConcurrentHashMap<>();

    public TeamScoreService(@NotNull Plugin plugin, @NotNull TeamHandler teamHandler) {
        this(plugin, teamHandler, ValueKind.UNKNOWN);
    }

    public TeamScoreService(@NotNull Plugin plugin, @NotNull TeamHandler teamHandler, @NotNull ValueKind parseHint) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teamHandler = Objects.requireNonNull(teamHandler, "teamHandler");
        this.parseHint = Objects.requireNonNull(parseHint, "parseHint");
    }

    public Optional<Double> computeTeamScore(@NotNull UUID anyMember, @NotNull String placeholder) {
        Objects.requireNonNull(anyMember, "anyMember");
        Objects.requireNonNull(placeholder, "placeholder");

        Optional<Set<UUID>> membersOpt = getCachedMembers(anyMember);
        if (membersOpt.isEmpty()) return Optional.empty();

        Set<UUID> members = new HashSet<>(membersOpt.get());
        if (members.isEmpty()) return Optional.empty();

        
        Set<UUID> validated = teamHandler.validateTeamMembers(members);
        if (!validated.isEmpty()) {
            members = validated;
        }

        PlaceholderValueProvider provider = providers.computeIfAbsent(placeholder, ph -> new PlaceholderValueProvider(plugin, ph, parseHint));

        boolean anyValue = false;
        double sum = 0.0d;
        for (UUID member : members) {
            try {
                Double val = provider.getValue(member);
                if (val != null) {
                    anyValue = true;
                    sum += val;
                }
            } catch (Throwable ignored) {
            }
        }

        final double finalSum = sum;
        final boolean finalAnyValue = anyValue;
        final int memberCount = members.size();
        com.blakube.bktops.plugin.debug.Debug.log(() -> "Team score for member " + anyMember + " via " + placeholder
                + ": sum=" + finalSum + " over " + memberCount + " member(s), hasValue=" + finalAnyValue);
        return anyValue ? Optional.of(sum) : Optional.empty();
    }

    




    public com.blakube.bktops.plugin.provider.ValueKind getDetectedValueKind(@NotNull String placeholder) {
        PlaceholderValueProvider provider = providers.get(placeholder);
        return provider != null
                ? provider.getDetectedValueKind()
                : com.blakube.bktops.plugin.provider.ValueKind.UNKNOWN;
    }

    public Optional<String> getTeamName(@NotNull UUID anyMember) {
        
        TimedEntry<String> te = nameCache.get(anyMember);
        long now = System.currentTimeMillis();
        if (te != null && (now - te.time) <= NAME_TTL_MILLIS) {
            return Optional.ofNullable(te.value);
        }
        Optional<String> name = teamHandler.getTeamDisplayName(anyMember);
        nameCache.put(anyMember, new TimedEntry<>(name.orElse(null), now));
        return name;
    }

    private Optional<Set<UUID>> getCachedMembers(@NotNull UUID anyMember) {
        long now = System.currentTimeMillis();
        TimedEntry<Set<UUID>> te = membersCache.get(anyMember);
        if (te != null && (now - te.time) <= MEMBERS_TTL_MILLIS) {
            return Optional.ofNullable(te.value).map(Collections::unmodifiableSet);
        }

        Optional<Set<UUID>> membersOpt = teamHandler.getTeamMembers(anyMember);
        
        Set<UUID> toStore = membersOpt.map(HashSet::new).orElse(null);
        membersCache.put(anyMember, new TimedEntry<>(toStore, now));
        return membersOpt.map(Collections::unmodifiableSet);
    }

    private static final class TimedEntry<T> {
        final T value;
        final long time;
        TimedEntry(T value, long time) {
            this.value = value;
            this.time = time;
        }
    }
}
