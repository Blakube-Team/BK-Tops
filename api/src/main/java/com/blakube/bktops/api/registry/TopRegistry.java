package com.blakube.bktops.api.registry;

import com.blakube.bktops.api.top.Top;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

public interface TopRegistry<K> {

    void register(@NotNull Top<K> top);

    boolean unregister(@NotNull String topId);

    @NotNull
    Optional<Top<K>> get(@NotNull String topId);

    @NotNull
    Collection<Top<K>> getAll();

    boolean isRegistered(@NotNull String topId);

    int size();

    void clear();
}