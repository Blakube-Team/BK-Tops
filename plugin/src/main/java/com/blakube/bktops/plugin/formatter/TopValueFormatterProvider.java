package com.blakube.bktops.plugin.formatter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;






public final class TopValueFormatterProvider {

    private static volatile TopValueFormatter instance;

    private TopValueFormatterProvider() {}

    public static void setInstance(@NotNull TopValueFormatter formatter) {
        instance = formatter;
    }

    @Nullable
    public static TopValueFormatter getInstance() {
        return instance;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static void unload() {
        instance = null;
    }
}
