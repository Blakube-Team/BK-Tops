package com.blakube.bktops.api;

import org.jetbrains.annotations.NotNull;

public final class TopAPIProvider {

    private static volatile TopAPI instance;

    @NotNull
    public static TopAPI getInstance() {
        TopAPI api = instance;
        if (api == null) {
            throw new IllegalStateException("TopAPI not initialized yet! Is BK-Tops plugin loaded?");
        }
        return api;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static void setInstance(@NotNull TopAPI api) {
        if (instance != null) {
            throw new IllegalStateException("TopAPI already initialized!");
        }
        instance = api;
    }

    public static void unload() {
        instance = null;
    }

    private TopAPIProvider() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}