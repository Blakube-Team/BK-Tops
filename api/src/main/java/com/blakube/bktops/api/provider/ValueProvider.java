package com.blakube.bktops.api.provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides values for identifiers.
 */
public interface ValueProvider<K> {

    @Nullable
    Double getValue(@NotNull K identifier);

    @NotNull
    String getName();

    default boolean requiresOnline() {
        return false;
    }

    default boolean isAvailable() {
        return true;
    }
}