package com.blakube.bktops.plugin.top;

import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.api.resolver.NameResolver;
import com.blakube.bktops.api.storage.TopStorage;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.api.timed.ResetSchedule;
import com.blakube.bktops.api.timed.TimedTop;
import com.blakube.bktops.api.event.dispatcher.TopEventDispatcher;
import com.blakube.bktops.plugin.provider.TimedValueProvider;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import com.blakube.bktops.plugin.storage.database.dao.SnapshotDAO;
import com.blakube.bktops.plugin.storage.database.dao.TimedMetaDAO;
import com.blakube.bktops.plugin.schedule.CronExpression;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class DefaultTimedTop<K> extends DefaultTop<K> implements TimedTop<K> {

    private final ResetSchedule resetSchedule;
    private final SnapshotDAO<K> snapshotDAO;
    private TimedMetaDAO metaDAO;
    private String cronExpression;
    private long nextResetTime;
    private long startTime;

    public DefaultTimedTop(@NotNull String id,
                           @NotNull TopConfig config,
                           @NotNull ValueProvider<K> valueProvider,
                           @NotNull NameResolver<K> nameResolver,
                           @NotNull TopStorage<K> storage,
                           @NotNull ResetSchedule resetSchedule,
                           @NotNull SnapshotDAO<K> snapshotDAO) {
        super(id, config, valueProvider, nameResolver, storage);
        this.resetSchedule = Objects.requireNonNull(resetSchedule, "resetSchedule cannot be null");
        this.snapshotDAO = Objects.requireNonNull(snapshotDAO, "snapshotDAO cannot be null");
        this.startTime = System.currentTimeMillis();
        this.nextResetTime = calculateNextResetTime();
    }

    public void initTimingPersistence(@NotNull TimedMetaDAO metaDAO, String cronExpression) {
        this.metaDAO = Objects.requireNonNull(metaDAO, "metaDAO cannot be null");
        this.cronExpression = (cronExpression != null && !cronExpression.isBlank()) ? cronExpression : null;

        CompletableFuture
                .supplyAsync(this.metaDAO::load, DatabaseExecutors.DB_EXECUTOR)
                .thenAccept(meta -> {
                    if (meta != null) {
                        this.startTime = meta.getStartTime();
                        this.nextResetTime = meta.getNextResetTime();
                    } else {
                        long now = System.currentTimeMillis();
                        this.startTime = now;
                        this.nextResetTime = calculateNextResetTime();
                        CompletableFuture.runAsync(() -> this.metaDAO.save(now, nextResetTime, null), DatabaseExecutors.DB_EXECUTOR);
                    }
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }

    @Override
    @NotNull
    public ResetSchedule getResetSchedule() {
        return resetSchedule;
    }

    @Override
    public boolean shouldReset() {
        return System.currentTimeMillis() >= nextResetTime;
    }

    @Override
    @NotNull
    public Duration getTimeUntilReset() {
        long remaining = nextResetTime - System.currentTimeMillis();
        return Duration.ofMillis(Math.max(0, remaining));
    }

    @Override
    public long getNextResetTime() {
        return nextResetTime;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public void reset() {
        resetAsync();
    }

    public CompletableFuture<Void> resetAsync() {

        return CompletableFuture.runAsync(() -> {
            try {

                updateAllSnapshotsSync();

                storage.clear(id);

                long previousStart = this.startTime;
                this.startTime = System.currentTimeMillis();
                this.nextResetTime = calculateNextResetTime();

                if (this.metaDAO != null) {
                    this.metaDAO.save(this.startTime, this.nextResetTime, previousStart);
                }

                // Dispatch timed top reset event
                TopEventDispatcher.fireTimedReset(
                        id,
                        resetSchedule.getType(),
                        previousStart,
                        this.startTime,
                        this.nextResetTime
                );

            } catch (Exception e) {
                System.err.println("[BK-Tops] Error during reset of " + id + ": " + e.getMessage());
                e.printStackTrace();
            }
        }, DatabaseExecutors.DB_EXECUTOR);
    }

    private void updateAllSnapshotsSync() {

        if (!(valueProvider instanceof TimedValueProvider)) {
            return;
        }

        TimedValueProvider<K> timedProvider = (TimedValueProvider<K>) valueProvider;
        ValueProvider<K> baseProvider = timedProvider.getBaseProvider();

        if (baseProvider == null) {
            return;
        }

        var entries = new ArrayList<>(getEntries());

        if (entries.isEmpty()) {
            return;
        }

        Map<K, Double> snapshotBatch = new HashMap<>();

        for (var entry : entries) {
            K identifier = entry.getIdentifier();

            Double currentValue = baseProvider.getValue(identifier);

            if (currentValue != null) {
                snapshotBatch.put(identifier, currentValue);
            }
        }

        timedProvider.updateSnapshotsBatch(snapshotBatch);

    }

    @Deprecated
    public void resetWithSnapshots() {
        resetAsync();
    }

    private long calculateNextResetTime() {
        Instant now = Instant.now();

        if (cronExpression != null) {
            try {
                CronExpression cron = CronExpression.parse(cronExpression);
                Instant next = cron.nextExecutionAfter(now);
                if (next != null) {
                    return next.toEpochMilli();
                }
            } catch (Exception ignored) {}
        }

        Instant next;
        switch (resetSchedule.getType()) {
            case HOURLY:
                next = now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);
                break;
            case DAILY:
                next = now.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
                break;
            case WEEKLY:
                next = now.truncatedTo(ChronoUnit.DAYS);
                int daysUntilMonday = (8 - next.atZone(ZoneId.systemDefault()).getDayOfWeek().getValue()) % 7;
                if (daysUntilMonday == 0 && now.isAfter(next)) {
                    daysUntilMonday = 7;
                }
                next = next.plus(daysUntilMonday, ChronoUnit.DAYS);
                break;
            case MONTHLY:
                next = now.truncatedTo(ChronoUnit.DAYS)
                        .atZone(ZoneId.systemDefault())
                        .withDayOfMonth(1)
                        .plusMonths(1)
                        .toInstant();
                break;
            case CUSTOM:
                next = now.plus(resetSchedule.getInterval());
                break;
            default:
                next = now != null ? now.plus(1, ChronoUnit.DAYS) : null;
        }
        return next.toEpochMilli();
    }

    @NotNull
    public SnapshotDAO<K> getSnapshotDAO() {
        return snapshotDAO;
    }

    @Override
    public String toString() {
        return "DefaultTimedTop{" +
                "id='" + id + '\'' +
                ", size=" + getCurrentSize() + "/" + config.getSize() +
                ", schedule=" + resetSchedule.getType() +
                ", nextReset=" + getTimeUntilReset().toHours() + "h" +
                ", snapshots=~" + snapshotDAO.count() +
                '}';
    }
}