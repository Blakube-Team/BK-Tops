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
import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import com.blakube.bktops.plugin.storage.database.dao.TopStorageDAO;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final AtomicBoolean enabled;
    private final List<BatchProcessEntry<K>> batchBuffer;
    private static final int BATCH_THRESHOLD = 20;
    private long lastBatchFlush = System.currentTimeMillis();
    private static final long BATCH_FLUSH_INTERVAL_MS = 5000;

    public DefaultTopProcessor(@NotNull String topId,
                               @NotNull TopConfig config,
                               @NotNull ValueProvider<K> valueProvider,
                               @NotNull NameResolver<K> nameResolver,
                               @NotNull TopStorage<K> storage,
                               @NotNull ProcessingQueue<K> queue,
                               @NotNull Consumer<UpdateResult<K>> resultConsumer) {
        this.topId = Objects.requireNonNull(topId, "topId cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.valueProvider = Objects.requireNonNull(valueProvider, "valueProvider cannot be null");
        this.nameResolver = Objects.requireNonNull(nameResolver, "nameResolver cannot be null");
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.queue = Objects.requireNonNull(queue, "queue cannot be null");
        this.resultConsumer = Objects.requireNonNull(resultConsumer, "resultConsumer cannot be null");
        this.enabled = new AtomicBoolean(true);
        this.batchBuffer = new ArrayList<>();
    }

    @Override
    public int processBatch(int batchSize) {
        if (!enabled.get() || batchSize <= 0) {
            return 0;
        }

        List<QueueEntry<K>> entries = queue.poll(batchSize);

        if (entries.size() >= 5) {
            processBatchOptimized(entries);
        } else {
            for (QueueEntry<K> entry : entries) {
                processEntry(entry);
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastBatchFlush > BATCH_FLUSH_INTERVAL_MS) {
            flushBatch();
            lastBatchFlush = now;
        }

        return entries.size();
    }

    private void processBatchOptimized(@NotNull List<QueueEntry<K>> entries) {
        List<BatchProcessEntry<K>> processEntries = new ArrayList<>();

        for (QueueEntry<K> entry : entries) {
            K identifier = entry.getIdentifier();

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

            processEntries.add(new BatchProcessEntry<>(identifier, displayName, value));
        }

        if (processEntries.isEmpty()) {
            return;
        }

        CompletableFuture.supplyAsync(() -> {
                    List<TopStorageDAO.BatchEntry<K>> batchEntries = new ArrayList<>();
                    for (BatchProcessEntry<K> pe : processEntries) {
                        batchEntries.add(new TopStorageDAO.BatchEntry<>(
                                pe.identifier,
                                pe.displayName,
                                pe.value
                        ));
                    }

                    if (storage instanceof com.blakube.bktops.plugin.storage.wrapper.TopStorageImpl) {
                        @SuppressWarnings("unchecked")
                        com.blakube.bktops.plugin.storage.wrapper.TopStorageImpl<K> impl =
                                (com.blakube.bktops.plugin.storage.wrapper.TopStorageImpl<K>) storage;
                        impl.saveBatch(batchEntries);
                    }

                    return processEntries;
                }, DatabaseExecutors.DB_EXECUTOR)
                .thenAccept(savedEntries -> {
                    for (BatchProcessEntry<K> pe : savedEntries) {
                        UpdateResult<K> result = UpdateResult.success(
                                pe.identifier,
                                null,
                                pe.value,
                                null,
                                null
                        );
                        resultConsumer.accept(result);
                    }
                })
                .exceptionally(ex -> {
                    for (BatchProcessEntry<K> pe : processEntries) {
                        resultConsumer.accept(UpdateResult.failure(pe.identifier, "Batch error: " + ex.getMessage()));
                    }
                    return null;
                });
    }

    private void processEntry(@NotNull QueueEntry<K> entry) {
        K identifier = entry.getIdentifier();

        String displayName = nameResolver.resolve(identifier);
        if (displayName == null) {
            resultConsumer.accept(UpdateResult.failure(identifier, "Failed to resolve display name"));
            return;
        }

        CompletableFuture
                .supplyAsync(() -> {
                    Optional<TopEntry<K>> oldEntryOpt = storage.getEntry(topId, identifier);
                    Double newValue = valueProvider.getValue(identifier);
                    return new Object[]{oldEntryOpt, newValue};
                }, DatabaseExecutors.DB_EXECUTOR)
                .thenCompose(arr -> {
                    @SuppressWarnings("unchecked")
                    Optional<TopEntry<K>> oldEntryOpt = (Optional<TopEntry<K>>) arr[0];
                    Double newValue = (Double) arr[1];

                    if (newValue == null) {
                        resultConsumer.accept(UpdateResult.failure(identifier, "Failed to get value from provider"));
                        return CompletableFuture.completedFuture(null);
                    }

                    Double oldValue = oldEntryOpt.map(TopEntry::getValue).orElse(null);
                    Integer oldPosition = oldEntryOpt.map(TopEntry::getPosition).orElse(null);

                    return CompletableFuture.supplyAsync(
                                    () -> storage.save(topId, identifier, displayName, newValue, config.getSize()),
                                    DatabaseExecutors.DB_EXECUTOR
                            )
                            .thenCompose(saved -> {
                                if (!saved && oldPosition == null) {
                                    resultConsumer.accept(UpdateResult.success(identifier, oldValue, newValue, null, null));
                                    return CompletableFuture.completedFuture(null);
                                }

                                return CompletableFuture.supplyAsync(
                                                () -> storage.getPosition(topId, identifier),
                                                DatabaseExecutors.DB_EXECUTOR
                                        )
                                        .thenAccept(newPos -> {
                                            Integer newPosition = (newPos == -1) ? null : newPos;

                                            UpdateResult<K> result = UpdateResult.success(
                                                    identifier, oldValue, newValue, oldPosition, newPosition
                                            );
                                            resultConsumer.accept(result);
                                        });
                            });
                })
                .exceptionally(ex -> {
                    resultConsumer.accept(UpdateResult.failure(identifier, "Unexpected error: " + ex.getMessage()));
                    return null;
                });
    }

    @Override
    public void processImmediate(@NotNull K identifier, @NotNull String reason) {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        if (!enabled.get()) {
            return;
        }

        flushBatch();

        QueueEntry<K> entry = new QueueEntry<>(identifier, Priority.CRITICAL, reason);
        processEntry(entry);
    }

    private void flushBatch() {
        synchronized (batchBuffer) {
            if (!batchBuffer.isEmpty()) {
                List<BatchProcessEntry<K>> toFlush = new ArrayList<>(batchBuffer);
                batchBuffer.clear();

                processBatchOptimized(toFlush.stream()
                        .map(pe -> new QueueEntry<>(pe.identifier, Priority.MEDIUM, "batch_flush"))
                        .toList()
                );
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);

        if (!enabled) {
            flushBatch();
        }
    }

    private static class BatchProcessEntry<K> {
        final K identifier;
        final String displayName;
        final double value;

        BatchProcessEntry(K identifier, String displayName, double value) {
            this.identifier = identifier;
            this.displayName = displayName;
            this.value = value;
        }
    }
}