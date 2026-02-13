package com.blakube.bktops.plugin.provider;

import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.plugin.storage.database.dao.SnapshotDAO;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class TimedValueProvider<K> implements ValueProvider<K> {

    private final String topId;
    private final ValueProvider<K> baseProvider;
    private final SnapshotDAO<K> snapshotDAO;

    private final Map<K, Double> snapshotCache = new ConcurrentHashMap<>();
    private final Map<K, CachedValue> currentValueCache = new ConcurrentHashMap<>();
    private static final long CURRENT_VALUE_TTL_MS = 100;

    private volatile boolean initialized = false;
    private final long startupGraceUntil;
    private final CompletableFuture<Void> initializationFuture;

    public TimedValueProvider(@NotNull String topId,
                              @NotNull ValueProvider<K> baseProvider,
                              @NotNull SnapshotDAO<K> snapshotDAO) {
        this.topId = Objects.requireNonNull(topId, "topId cannot be null");
        this.baseProvider = Objects.requireNonNull(baseProvider, "baseProvider cannot be null");
        this.snapshotDAO = Objects.requireNonNull(snapshotDAO, "snapshotDAO cannot be null");
        // 10 seconds grace period after construction to allow dependent plugins/placeholders to stabilize
        this.startupGraceUntil = System.currentTimeMillis() + 10_000L;

        this.initializationFuture = CompletableFuture.runAsync(() -> {
            Map<K, Double> existing = snapshotDAO.getAllSnapshots();
            snapshotCache.putAll(existing);
            initialized = true;
        }, DatabaseExecutors.DB_EXECUTOR);
    }

    @Override
    @Nullable
    public Double getValue(@NotNull K identifier) {
        Objects.requireNonNull(identifier, "identifier cannot be null");

        if (!initialized) {
            return null;
        }

        Double currentValue = getCurrentValueCached(identifier);

        if (currentValue == null) {
            return null;
        }
        if (currentValue <= 0.0) {
            return null;
        }

        Double snapshotValue = snapshotCache.get(identifier);
        if (snapshotValue == null) {
            long now = System.currentTimeMillis();
            if (now < startupGraceUntil) {
                return null;
            }
            snapshotValue = currentValue;
            snapshotCache.put(identifier, snapshotValue);
            Double finalSnapshotValue = snapshotValue;
            CompletableFuture.runAsync(() -> snapshotDAO.setSnapshot(identifier, finalSnapshotValue), DatabaseExecutors.DB_EXECUTOR);
        }

        double result = Math.max(0.0, currentValue - snapshotValue);

        return result;
    }

    private Double getCurrentValueCached(@NotNull K identifier) {
        long now = System.currentTimeMillis();
        CachedValue cached = currentValueCache.get(identifier);

        if (cached != null && (now - cached.timestamp) < CURRENT_VALUE_TTL_MS) {
            return cached.value;
        }

        Double fresh = baseProvider.getValue(identifier);
        if (fresh != null) {
            currentValueCache.put(identifier, new CachedValue(fresh, now));
        }

        return fresh;
    }

    @Override
    @NotNull
    public String getName() {
        return "Timed[" + baseProvider.getName() + "]";
    }

    @Override
    public boolean requiresOnline() {
        return baseProvider.requiresOnline();
    }

    @Override
    public boolean isAvailable() {
        return baseProvider.isAvailable();
    }

    @NotNull
    public ValueProvider<K> getBaseProvider() {
        return baseProvider;
    }

    @NotNull
    public SnapshotDAO<K> getSnapshotDAO() {
        return snapshotDAO;
    }

    public void updateSnapshotsBatch(@NotNull Map<K, Double> snapshots) {
        snapshotCache.putAll(snapshots);
        currentValueCache.clear();
        CompletableFuture.runAsync(() -> snapshotDAO.saveBatch(snapshots), DatabaseExecutors.DB_EXECUTOR);
    }

    public void clearCache() {
        snapshotCache.clear();
        currentValueCache.clear();
    }

    public boolean isInitialized() {
        return initialized;
    }

    @NotNull
    public CompletableFuture<Void> getInitializationFuture() {
        return initializationFuture;
    }

    @NotNull
    public Map<K, Double> getSnapshotCache() {
        return new ConcurrentHashMap<>(snapshotCache);
    }

    private static class CachedValue {
        final double value;
        final long timestamp;

        CachedValue(double value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}