package com.blakube.bktops.plugin.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerNameCache {

    private static final ConcurrentHashMap<UUID, String> NAMES = new ConcurrentHashMap<>(512);

    private PlayerNameCache() {}

    public static void put(@NotNull UUID uuid, @NotNull String name) {
        NAMES.put(uuid, name);
    }

    @Nullable
    public static String get(@NotNull UUID uuid) {
        return NAMES.get(uuid);
    }

    public static void clear() {
        NAMES.clear();
    }
}
