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
import com.blakube.bktops.plugin.debug.Debug;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import com.blakube.bktops.plugin.storage.database.dao.TopStorageDAO;
import com.blakube.bktops.plugin.storage.wrapper.TopStorageImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DefaultTopProcessor<K> implements TopProcessor<K> {

    private final JavaPlugin plugin;
    private final String topId;
    private final TopConfig config;
    private final ValueProvider<K> valueProvider;
    private final NameResolver<K> nameResolver;
    private final TopStorage<K> storage;
    private final ProcessingQueue<K> queue;
    private final Consumer<List<UpdateResult<K>>> batchResultConsumer;
    private final Consumer<K> entryRemover;
    private final AtomicBoolean enabled;

    public DefaultTopProcessor(@NotNull JavaPlugin plugin,
                               @NotNull String topId,
                               @NotNull TopConfig config,
                               @NotNull ValueProvider<K> valueProvider,
                               @NotNull NameResolver<K> nameResolver,
                               @NotNull TopStorage<K> storage,
                               @NotNull ProcessingQueue<K> queue,
                               @NotNull Consumer<List<UpdateResult<K>>> batchResultConsumer,
                               @NotNull Consumer<K> entryRemover) {
        this.plugin              = Objects.requireNonNull(plugin,              "plugin cannot be null");
        this.topId               = Objects.requireNonNull(topId,               "topId cannot be null");
        this.config              = Objects.requireNonNull(config,              "config cannot be null");
        this.valueProvider       = Objects.requireNonNull(valueProvider,       "valueProvider cannot be null");
        this.nameResolver        = Objects.requireNonNull(nameResolver,        "nameResolver cannot be null");
        this.storage             = Objects.requireNonNull(storage,             "storage cannot be null");
        this.queue               = Objects.requireNonNull(queue,               "queue cannot be null");
        this.batchResultConsumer = Objects.requireNonNull(batchResultConsumer, "batchResultConsumer cannot be null");
        this.entryRemover        = Objects.requireNonNull(entryRemover,        "entryRemover cannot be null");
        this.enabled             = new AtomicBoolean(true);
    }

    @Override
    public int processBatch(int batchSize) {
        if (!enabled.get() || batchSize <= 0) return 0;

        List<QueueEntry<K>> entries = queue.poll(batchSize);
        if (entries.isEmpty()) return 0;

        processBatchOptimized(entries);
        return entries.size();
    }

    private void processBatchOptimized(@NotNull List<QueueEntry<K>> entries) {
        if (Bukkit.isPrimaryThread()) {
            List<PreResolved<K>> preResolved = collectValues(entries);
            if (preResolved.isEmpty()) return;
            CompletableFuture
                    .runAsync(() -> dispatchPhase2(preResolved), DatabaseExecutors.DB_EXECUTOR)
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[BK-Tops] Batch error: " + ex.getMessage());
                        return null;
                    });
        } else {
            if (!plugin.isEnabled()) return;
            CompletableFuture<List<PreResolved<K>>> collectFuture = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    collectFuture.complete(collectValues(entries));
                } catch (Throwable t) {
                    collectFuture.completeExceptionally(t);
                }
            });
            collectFuture
                    .thenAcceptAsync(preResolved -> {
                        if (!preResolved.isEmpty()) dispatchPhase2(preResolved);
                    }, DatabaseExecutors.DB_EXECUTOR)
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[BK-Tops] Batch error: " + ex.getMessage());
                        return null;
                    });
        }
    }

    private List<PreResolved<K>> collectValues(@NotNull List<QueueEntry<K>> entries) {
        List<PreResolved<K>> preResolved = new ArrayList<>(entries.size());
        boolean isPlayerTop = nameResolver instanceof com.blakube.bktops.plugin.resolver.PlayerNameResolver;

        for (QueueEntry<K> entry : entries) {
            K identifier = entry.getIdentifier();
            if (shouldBypass(identifier)) {
                Debug.log("[{}] Skipping {} (bypass permission)", topId, identifier);
                continue;
            }

            if (isPlayerTop && !config.getConditionSet().isEmpty() && identifier instanceof UUID uuid) {
                if (!ConditionEvaluator.passes(config.getConditionSet(), uuid)) {
                    Debug.log("[{}] Skipping {} (failed conditions), removing from top", topId, identifier);
                    entryRemover.accept(identifier);
                    continue;
                }
            }

            Double value = valueProvider.getValue(identifier);
            if (value == null) {
                Debug.log("[{}] Skipping {} (null value from {})", topId, identifier, valueProvider.getName());
                continue;
            }
            if (value == 0.0 && !config.isAllowZeroValues()) {
                Debug.log("[{}] Skipping {} (zero value, allow-zero=false)", topId, identifier);
                continue;
            }

            Debug.log("[{}] Collected value {} for {}", topId, value, identifier);
            preResolved.add(new PreResolved<>(identifier, value));
        }
        return preResolved;
    }

    private void dispatchPhase2(@NotNull List<PreResolved<K>> preResolved) {
        record Resolved<K>(K identifier, String displayName, double value) {}

        List<Resolved<K>> resolved = new ArrayList<>(preResolved.size());
        for (PreResolved<K> pre : preResolved) {
            String displayName = nameResolver.resolve(pre.identifier());
            if (displayName == null) continue;
            resolved.add(new Resolved<>(pre.identifier(), displayName, pre.value()));
        }
        if (resolved.isEmpty()) return;

        if (storage instanceof TopStorageImpl<K> impl) {
            List<TopStorageDAO.BatchEntry<K>> batch = new ArrayList<>(resolved.size());
            for (Resolved<K> r : resolved) {
                batch.add(new TopStorageDAO.BatchEntry<>(r.identifier(), r.displayName(), r.value()));
            }
            impl.saveBatch(batch, config.getSize());
        } else {
            for (Resolved<K> r : resolved) {
                storage.save(topId, r.identifier(), r.displayName(), r.value(), config.getSize());
            }
        }

        List<UpdateResult<K>> results = new ArrayList<>(resolved.size());
        for (Resolved<K> r : resolved) {
            results.add(UpdateResult.success(r.identifier(), r.displayName(), null, r.value(), null, null));
        }
        batchResultConsumer.accept(results);
    }

    @Override
    public void processImmediate(@NotNull K identifier, @NotNull String reason) {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        Objects.requireNonNull(reason,     "reason cannot be null");
        if (!enabled.get()) return;
        if (shouldBypass(identifier)) return;
        processBatchOptimized(List.of(new QueueEntry<>(identifier, Priority.CRITICAL, reason)));
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

    private record PreResolved<K>(K identifier, double value) {}
}
