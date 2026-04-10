package com.blakube.bktops.plugin.processor;

import com.blakube.bktops.api.processor.TopProcessor;
import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.queue.ProcessingQueue;
import com.blakube.bktops.api.queue.QueueEntry;
import com.blakube.bktops.api.resolver.NameResolver;
import com.blakube.bktops.api.result.UpdateResult;
import com.blakube.bktops.api.storage.TopStorage;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.plugin.condition.ConditionEvaluator;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import com.blakube.bktops.plugin.storage.database.dao.TopStorageDAO;
import com.blakube.bktops.plugin.storage.wrapper.TopStorageImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DefaultTopProcessor<K> implements TopProcessor<K> {

    private final String topId;
    private final TopConfig config;
    private final ValueProvider<K> valueProvider;
    private final NameResolver<K> nameResolver;
    private final TopStorage<K> storage;
    private final ProcessingQueue<K> queue;
    private final Consumer<UpdateResult<K>> resultConsumer;
    private final Consumer<K> entryRemover;
    private final AtomicBoolean enabled;

    public DefaultTopProcessor(@NotNull String topId,
                               @NotNull TopConfig config,
                               @NotNull ValueProvider<K> valueProvider,
                               @NotNull NameResolver<K> nameResolver,
                               @NotNull TopStorage<K> storage,
                               @NotNull ProcessingQueue<K> queue,
                               @NotNull Consumer<UpdateResult<K>> resultConsumer,
                               @NotNull Consumer<K> entryRemover) {
        this.topId          = Objects.requireNonNull(topId,          "topId cannot be null");
        this.config         = Objects.requireNonNull(config,         "config cannot be null");
        this.valueProvider  = Objects.requireNonNull(valueProvider,  "valueProvider cannot be null");
        this.nameResolver   = Objects.requireNonNull(nameResolver,   "nameResolver cannot be null");
        this.storage        = Objects.requireNonNull(storage,        "storage cannot be null");
        this.queue          = Objects.requireNonNull(queue,          "queue cannot be null");
        this.resultConsumer = Objects.requireNonNull(resultConsumer, "resultConsumer cannot be null");
        this.entryRemover   = Objects.requireNonNull(entryRemover,   "entryRemover cannot be null");
        this.enabled        = new AtomicBoolean(true);
    }

    @Override
    public int processBatch(int batchSize) {
        if (!enabled.get() || batchSize <= 0) return 0;

        List<QueueEntry<K>> entries = queue.poll(batchSize);
        if (entries.isEmpty()) return 0;

        if (entries.size() >= 5) {
            processBatchOptimized(entries);
        } else {
            for (QueueEntry<K> entry : entries) {
                processEntry(entry);
            }
        }

        return entries.size();
    }

    private void processBatchOptimized(@NotNull List<QueueEntry<K>> entries) {
        record Resolved<K>(K identifier, String displayName, double value) {}

        List<Resolved<K>> resolved = new ArrayList<>(entries.size());

        for (QueueEntry<K> entry : entries) {
            K identifier = entry.getIdentifier();
            if (shouldBypass(identifier)) continue;

            if (!config.getConditionSet().isEmpty() && identifier instanceof UUID uuid) {
                if (!ConditionEvaluator.passes(config.getConditionSet(), uuid)) {
                    entryRemover.accept(identifier);
                    continue;
                }
            }

            String displayName = nameResolver.resolve(identifier);
            if (displayName == null) {
                resultConsumer.accept(UpdateResult.failure(identifier, "Failed to resolve display name"));
                continue;
            }

            Double value = valueProvider.getValue(identifier);
            if (value == null) {
                resultConsumer.accept(UpdateResult.failure(identifier, "Failed to get value from provider"));
                continue;
            }

            if (value == 0.0) continue;

            resolved.add(new Resolved<>(identifier, displayName, value));
        }

        if (resolved.isEmpty()) return;

        CompletableFuture
                .runAsync(() -> {
                    if (storage instanceof TopStorageImpl<K> impl) {
                        List<TopStorageDAO.BatchEntry<K>> batch = new ArrayList<>(resolved.size());
                        for (Resolved<K> r : resolved) {
                            batch.add(new TopStorageDAO.BatchEntry<>(r.identifier(), r.displayName(), r.value()));
                        }
                        impl.saveBatch(batch, config.getSize()); // save + trimToMaxSize in one call
                    } else {
                        for (Resolved<K> r : resolved) {
                            storage.save(topId, r.identifier(), r.displayName(), r.value(), config.getSize());
                        }
                    }
                }, DatabaseExecutors.DB_EXECUTOR)
                .thenRun(() -> {
                    for (Resolved<K> r : resolved) {
                        resultConsumer.accept(UpdateResult.success(
                                r.identifier(), r.displayName(), null, r.value(), null, null));
                    }
                })
                .exceptionally(ex -> {
                    for (Resolved<K> r : resolved) {
                        resultConsumer.accept(UpdateResult.failure(r.identifier(),
                                "Batch error: " + ex.getMessage()));
                    }
                    return null;
                });
    }

    private void processEntry(@NotNull QueueEntry<K> entry) {
        K identifier = entry.getIdentifier();
        if (shouldBypass(identifier)) return;

        String displayName = nameResolver.resolve(identifier);
        if (displayName == null) {
            resultConsumer.accept(UpdateResult.failure(identifier, "Failed to resolve display name"));
            return;
        }

        if (!config.getConditionSet().isEmpty() && identifier instanceof UUID uuid) {
            if (!ConditionEvaluator.passes(config.getConditionSet(), uuid)) {
                entryRemover.accept(identifier);
                return;
            }
        }

        Double newValue = valueProvider.getValue(identifier);
        if (newValue == null) {
            resultConsumer.accept(UpdateResult.failure(identifier, "Failed to get value from provider"));
            return;
        }

        if (newValue == 0.0) return;

        CompletableFuture
                .runAsync(() -> storage.save(topId, identifier, displayName, newValue, config.getSize()),
                        DatabaseExecutors.DB_EXECUTOR)
                .thenRun(() -> resultConsumer.accept(
                        UpdateResult.success(identifier, displayName, null, newValue, null, null)))
                .exceptionally(ex -> {
                    resultConsumer.accept(UpdateResult.failure(identifier,
                            "Unexpected error: " + ex.getMessage()));
                    return null;
                });
    }

    @Override
    public void processImmediate(@NotNull K identifier, @NotNull String reason) {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        Objects.requireNonNull(reason,     "reason cannot be null");
        if (!enabled.get()) return;
        if (shouldBypass(identifier)) return;
        processEntry(new QueueEntry<>(identifier, Priority.CRITICAL, reason));
    }

    private boolean shouldBypass(@NotNull K identifier) {
        if (!(identifier instanceof UUID uuid)) return false;
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.hasPermission("bktops.bypass." + topId);
    }

    @Override
    public boolean isEnabled() { return enabled.get(); }

    @Override
    public void setEnabled(boolean enabled) { this.enabled.set(enabled); }
}
