package com.blakube.bktops.api.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves display names for identifiers.
 */
public interface NameResolver<K> {
    @Nullable
    String resolve(@NotNull K identifier);
}