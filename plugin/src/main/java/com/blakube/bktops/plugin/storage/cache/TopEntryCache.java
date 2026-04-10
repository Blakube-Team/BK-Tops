package com.blakube.bktops.plugin.storage.cache;

import com.blakube.bktops.api.top.TopEntry;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class TopEntryCache<K> {

    private static final class State<K> {
        final List<TopEntry<K>> entries;
        final Map<K, Integer>   index;

        State(List<TopEntry<K>> entries, Map<K, Integer> index) {
            this.entries = entries;
            this.index   = index;
        }
    }

    private final AtomicReference<State<K>> stateRef =
            new AtomicReference<>(new State<>(List.of(), Map.of()));

    public TopEntryCache() {}

    public void setEntries(@NotNull List<TopEntry<K>> entries) {
        List<TopEntry<K>>  snapshot = new ArrayList<>(entries.size());
        HashMap<K, Integer> newIndex = new HashMap<>(entries.size() * 2);

        for (int i = 0; i < entries.size(); i++) {
            TopEntry<K> e   = entries.get(i);
            int         pos = i + 1;
            snapshot.add(pos == e.getPosition()
                    ? e
                    : new TopEntry<>(e.getIdentifier(), e.getDisplayName(), e.getValue(), pos, e.getLastUpdated()));
            newIndex.put(e.getIdentifier(), pos);
        }

        stateRef.set(new State<>(
                Collections.unmodifiableList(snapshot),
                Collections.unmodifiableMap(newIndex)
        ));
    }

    public int updateEntry(@NotNull K identifier, @NotNull String displayName, double newValue, int maxSize) {
        while (true) {
            State<K> current = stateRef.get();

            List<TopEntry<K>> working = new ArrayList<>(current.entries.size() + 1);
            for (TopEntry<K> e : current.entries) {
                if (!e.getIdentifier().equals(identifier)) {
                    working.add(e);
                }
            }

            boolean qualifies = working.size() < maxSize
                    || (!working.isEmpty() && newValue > working.get(working.size() - 1).getValue());

            if (qualifies) {
                working.add(new TopEntry<>(identifier, displayName, newValue, 0));
                Collections.sort(working); // TopEntry.compareTo sorts value DESC
                if (working.size() > maxSize) {
                    working = new ArrayList<>(working.subList(0, maxSize));
                }
            }

            List<TopEntry<K>>   finalList = new ArrayList<>(working.size());
            HashMap<K, Integer> newIndex  = new HashMap<>(working.size() * 2);
            for (int i = 0; i < working.size(); i++) {
                TopEntry<K> e   = working.get(i);
                int         pos = i + 1;
                finalList.add(new TopEntry<>(e.getIdentifier(), e.getDisplayName(), e.getValue(), pos, e.getLastUpdated()));
                newIndex.put(e.getIdentifier(), pos);
            }

            State<K> next = new State<>(
                    Collections.unmodifiableList(finalList),
                    Collections.unmodifiableMap(newIndex)
            );

            if (stateRef.compareAndSet(current, next)) {
                return newIndex.getOrDefault(identifier, -1);
            }
        }
    }

    public boolean removeEntry(@NotNull K identifier) {
        while (true) {
            State<K> current = stateRef.get();
            if (!current.index.containsKey(identifier)) return false;

            List<TopEntry<K>> working = new ArrayList<>(current.entries.size());
            for (TopEntry<K> e : current.entries) {
                if (!e.getIdentifier().equals(identifier)) working.add(e);
            }

            List<TopEntry<K>>   finalList = new ArrayList<>(working.size());
            HashMap<K, Integer> newIndex  = new HashMap<>(working.size() * 2);
            for (int i = 0; i < working.size(); i++) {
                TopEntry<K> e   = working.get(i);
                int         pos = i + 1;
                finalList.add(new TopEntry<>(e.getIdentifier(), e.getDisplayName(), e.getValue(), pos, e.getLastUpdated()));
                newIndex.put(e.getIdentifier(), pos);
            }

            State<K> next = new State<>(
                    Collections.unmodifiableList(finalList),
                    Collections.unmodifiableMap(newIndex)
            );

            if (stateRef.compareAndSet(current, next)) return true;
        }
    }

    @NotNull
    public List<TopEntry<K>> getEntriesCopy() {
        return new ArrayList<>(stateRef.get().entries);
    }

    @NotNull
    public Optional<TopEntry<K>> getEntryAt(int position) {
        List<TopEntry<K>> entries = stateRef.get().entries;
        if (position < 1 || position > entries.size()) return Optional.empty();
        return Optional.of(entries.get(position - 1));
    }

    @NotNull
    public Optional<TopEntry<K>> getEntryByIdentifier(@NotNull K identifier) {
        State<K> s   = stateRef.get();
        Integer  pos = s.index.get(identifier);
        if (pos == null || pos < 1 || pos > s.entries.size()) return Optional.empty();
        return Optional.of(s.entries.get(pos - 1));
    }

    public int getPosition(@NotNull K identifier) {
        return stateRef.get().index.getOrDefault(identifier, -1);
    }

    @NotNull
    public Optional<Double> getMinValue() {
        List<TopEntry<K>> entries = stateRef.get().entries;
        if (entries.isEmpty()) return Optional.empty();
        return Optional.of(entries.getLast().getValue());
    }

    @NotNull
    public Optional<Double> getMaxValue() {
        List<TopEntry<K>> entries = stateRef.get().entries;
        if (entries.isEmpty()) return Optional.empty();
        return Optional.of(entries.getFirst().getValue());
    }

    public int size() {
        return stateRef.get().entries.size();
    }
}
