package com.blakube.bktops.plugin.formatter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Singleton provider for NumberFormatter.
 * Allows global access to the formatter without dependency injection.
 */
public final class NumberFormatterProvider {

    private static NumberFormatter instance;

    private NumberFormatterProvider() {}

    public static void setInstance(@NotNull NumberFormatter formatter) {
        instance = formatter;
    }

    @Nullable
    public static NumberFormatter getInstance() {
        return instance;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static void unload() {
        instance = null;
    }
}