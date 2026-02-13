package com.blakube.bktops.plugin.storage.cache;

import com.blakube.bktops.api.top.TopEntry;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class TopEntryCache<K> {

    private final AtomicReference<List<TopEntry<K>>> entriesRef = new AtomicReference<>(List.of());
    private final ConcurrentHashMap<K, Integer> positionIndex = new ConcurrentHashMap<>();

    public TopEntryCache() {}

    public void setEntries(@NotNull List<TopEntry<K>> entries) {
        List<TopEntry<K>> snapshot = List.copyOf(entries);
        positionIndex.clear();
        for (int i = 0; i < snapshot.size(); i++) {
            TopEntry<K> e = snapshot.get(i);
            positionIndex.put(e.getIdentifier(), i + 1);
        }
        entriesRef.set(snapshot);
    }

    @NotNull
    public List<TopEntry<K>> getEntriesCopy() {
        List<TopEntry<K>> current = entriesRef.get();
        return new ArrayList<>(current);
    }

    @NotNull
    public Optional<TopEntry<K>> getEntryAt(int position) {
        List<TopEntry<K>> current = entriesRef.get();
        if (position < 1 || position > current.size()) return Optional.empty();
        return Optional.of(current.get(position - 1));
    }

    public int getPosition(@NotNull K identifier) {
        Integer pos = positionIndex.get(identifier);
        return pos == null ? -1 : pos;
    }

    @NotNull
    public Optional<Double> getMinValue() {
        List<TopEntry<K>> current = entriesRef.get();
        if (current.isEmpty()) return Optional.empty();
        return Optional.of(current.getLast().getValue());
    }

    @NotNull
    public Optional<Double> getMaxValue() {
        List<TopEntry<K>> current = entriesRef.get();
        if (current.isEmpty()) return Optional.empty();
        return Optional.of(current.getFirst().getValue());
    }

    public int size() {
        return entriesRef.get().size();
    }
}
