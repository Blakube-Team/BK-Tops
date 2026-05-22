package com.blakube.bktops.api.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface NameResolver<K> {
    @Nullable
    String resolve(@NotNull K identifier);
}