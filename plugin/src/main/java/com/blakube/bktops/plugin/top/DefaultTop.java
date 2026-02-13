package com.blakube.bktops.plugin.top;

import com.blakube.bktops.api.event.dispatcher.TopEventDispatcher;
import com.blakube.bktops.api.processor.TopProcessor;
import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.queue.ProcessingQueue;
import com.blakube.bktops.api.resolver.NameResolver;
import com.blakube.bktops.api.result.UpdateResult;
import com.blakube.bktops.api.storage.TopStorage;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.processor.DefaultTopProcessor;
import com.blakube.bktops.plugin.queue.PriorityProcessingQueue;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import com.blakube.bktops.plugin.storage.cache.TopEntryCache;

public class DefaultTop<K> implements Top<K> {

    protected final String id;
    protected final TopConfig config;
    protected final ValueProvider<K> valueProvider;
    protected final NameResolver<K> nameResolver;
    protected final TopStorage<K> storage;
    protected final ProcessingQueue<K> queue;
    protected final TopProcessor<K> processor;

    protected final TopEntryCache<K> cache;

    public DefaultTop(@NotNull String id,
                     @NotNull TopConfig config,
                     @NotNull ValueProvider<K> valueProvider,
                     @NotNull NameResolver<K> nameResolver,
                     @NotNull TopStorage<K> storage) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.valueProvider = Objects.requireNonNull(valueProvider, "valueProvider cannot be null");
        this.nameResolver = Objects.requireNonNull(nameResolver, "nameResolver cannot be null");
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.queue = new PriorityProcessingQueue<>();
        this.cache = new TopEntryCache<>();

        this.processor = new DefaultTopProcessor<>(
            id,
            config,
            valueProvider,
            nameResolver,
            storage,
            queue,
            this::onUpdateResult
        );

        asyncLoadFromStorage();
    }

    protected void asyncLoadFromStorage() {
        CompletableFuture
            .supplyAsync(() -> storage.load(id), DatabaseExecutors.DB_EXECUTOR)
            .thenAccept(cache::setEntries)
            .exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
    }

    protected void onUpdateResult(@NotNull UpdateResult<K> result) {
        if (result.isSuccess()) {
            // Fire event if relevant changes happened
            boolean changed = result.hasPositionChanged() || result.isEnteredTop() || result.isLeftTop() || result.hasValueChanged();
            if (changed) {
                String displayName = null;
                try {
                    displayName = nameResolver.resolve(result.getIdentifier());
                } catch (Exception ignored) {}
                TopEventDispatcher.firePositionUpdate(
                        id,
                        result.getIdentifier(),
                        displayName,
                        result.getOldValue(),
                        result.getNewValue(),
                        result.getOldPosition(),
                        result.getNewPosition()
                );
            }
            refresh();
        }
    }

    @Override
    @NotNull
    public String getId() {
        return id;
    }

    @Override
    @NotNull
    public TopConfig getConfig() {
        return config;
    }

    @Override
    @NotNull
    public List<TopEntry<K>> getEntries() {
        return cache.getEntriesCopy();
    }

    @Override
    @NotNull
    public Optional<TopEntry<K>> getEntry(int position) {
        if (position < 1) {
            return Optional.empty();
        }

        return cache.getEntryAt(position);
    }

    @Override
    public int getPosition(@NotNull K identifier) {
        Objects.requireNonNull(identifier, "identifier cannot be null");

        return cache.getPosition(identifier);
    }

    @Override
    public boolean isInTop(@NotNull K identifier) {
        return getPosition(identifier) != -1;
    }

    @Override
    @NotNull
    public Optional<Double> getMinValue() {
        return cache.getMinValue();
    }

    @Override
    @NotNull
    public Optional<Double> getMaxValue() {
        return cache.getMaxValue();
    }

    @Override
    public int getCurrentSize() {
        return cache.size();
    }

    @Override
    public void markDirty(@NotNull K identifier, @NotNull String reason) {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        queue.enqueue(identifier, Priority.CRITICAL, reason);
    }

    @Override
    public void enqueue(@NotNull Collection<K> identifiers,
                       @NotNull Priority priority,
                       @NotNull String reason) {
        Objects.requireNonNull(identifiers, "identifiers cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        queue.enqueueAll(identifiers, priority, reason);
    }

    @Override
    @NotNull
    public ValueProvider<K> getValueProvider() {
        return valueProvider;
    }

    @Override
    @NotNull
    public ProcessingQueue<K> getQueue() {
        return queue;
    }

    @Override
    @NotNull
    public TopProcessor<K> getProcessor() {
        return processor;
    }

    @Override
    public void refresh() {
        asyncLoadFromStorage();
    }

    @Override
    public void reset() {
        CompletableFuture.runAsync(() -> storage.clear(id), DatabaseExecutors.DB_EXECUTOR);
        cache.setEntries(Collections.emptyList());
        queue.clear();
    }

    @Override
    public String toString() {
        return "DefaultTop{" +
                "id='" + id + '\'' +
                ", size=" + getCurrentSize() + "/" + config.getSize() +
                ", queueSize=" + queue.size() +
                '}';
    }
}