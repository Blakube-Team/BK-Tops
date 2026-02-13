package com.blakube.bktops.plugin.registry;

import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.top.Top;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultTopRegistry<K> implements TopRegistry<K> {

    private final Map<String, Top<K>> tops;

    public DefaultTopRegistry() {
        this.tops = new ConcurrentHashMap<>();
    }

    @Override
    public void register(@NotNull Top<K> top) {
        Objects.requireNonNull(top, "top cannot be null");

        String id = top.getId();
        if (tops.containsKey(id)) {
            throw new IllegalArgumentException("Top with id '" + id + "' is already registered");
        }

        tops.put(id, top);
    }

    @Override
    public boolean unregister(@NotNull String topId) {
        Objects.requireNonNull(topId, "topId cannot be null");
        return tops.remove(topId) != null;
    }

    @Override
    @NotNull
    public Optional<Top<K>> get(@NotNull String topId) {
        Objects.requireNonNull(topId, "topId cannot be null");
        return Optional.ofNullable(tops.get(topId));
    }

    @Override
    @NotNull
    public Collection<Top<K>> getAll() {
        return Collections.unmodifiableCollection(tops.values());
    }

    @Override
    public boolean isRegistered(@NotNull String topId) {
        Objects.requireNonNull(topId, "topId cannot be null");
        return tops.containsKey(topId);
    }

    @Override
    public int size() {
        return tops.size();
    }

    @Override
    public void clear() {
        tops.clear();
    }
}