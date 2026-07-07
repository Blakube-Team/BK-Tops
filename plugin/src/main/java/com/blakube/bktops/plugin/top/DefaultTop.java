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
import com.blakube.bktops.plugin.cache.PlayerNameCache;
import com.blakube.bktops.plugin.debug.Debug;
import com.blakube.bktops.plugin.formatter.TopValueFormatterProvider;
import com.blakube.bktops.plugin.formatter.ValueFormatter;
import com.blakube.bktops.plugin.processor.DefaultTopProcessor;
import com.blakube.bktops.plugin.queue.PriorityProcessingQueue;
import com.blakube.bktops.plugin.resolver.PlayerNameResolver;
import com.blakube.bktops.plugin.storage.cache.TopEntryCache;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DefaultTop<K> implements Top<K> {

    protected final JavaPlugin plugin;
    protected final String id;
    protected final TopConfig config;
    protected final ValueProvider<K> valueProvider;
    protected final NameResolver<K> nameResolver;
    protected final TopStorage<K> storage;
    protected final ProcessingQueue<K> queue;
    protected final TopProcessor<K> processor;
    protected final TopEntryCache<K> cache;
    private final Object updateLock = new Object();

    public DefaultTop(@NotNull JavaPlugin plugin,
                      @NotNull String id,
                      @NotNull TopConfig config,
                      @NotNull ValueProvider<K> valueProvider,
                      @NotNull NameResolver<K> nameResolver,
                      @NotNull TopStorage<K> storage) {
        this.plugin        = Objects.requireNonNull(plugin,        "plugin cannot be null");
        this.id            = Objects.requireNonNull(id,            "id cannot be null");
        this.config        = Objects.requireNonNull(config,        "config cannot be null");
        this.valueProvider = Objects.requireNonNull(valueProvider, "valueProvider cannot be null");
        this.nameResolver  = Objects.requireNonNull(nameResolver,  "nameResolver cannot be null");
        this.storage       = Objects.requireNonNull(storage,       "storage cannot be null");
        this.queue         = new PriorityProcessingQueue<>();
        this.cache         = new TopEntryCache<>();

        this.processor = new DefaultTopProcessor<>(
                plugin,
                id,
                config,
                valueProvider,
                nameResolver,
                storage,
                queue,
                this::onBatchUpdateResult,
                this::removeFromTop
        );

        asyncLoadFromStorage();
    }

    protected void asyncLoadFromStorage() {
        CompletableFuture
                .supplyAsync(() -> storage.load(id, config.getSize()), DatabaseExecutors.DB_EXECUTOR)
                .thenAccept(entries -> {
                    cache.setEntries(entries);
                    if (nameResolver instanceof PlayerNameResolver) {
                        for (TopEntry<K> entry : entries) {
                            if (entry.getIdentifier() instanceof UUID uuid && entry.getDisplayName() != null) {
                                PlayerNameCache.put(uuid, entry.getDisplayName());
                            }
                        }
                    }
                })
                .exceptionally(ex -> { ex.printStackTrace(); return null; });
    }

    
    
    protected void onBatchUpdateResult(@NotNull List<UpdateResult<K>> results) {
        if (results.isEmpty()) return;

        synchronized (updateLock) {
            handleBatchUpdateResult(results);
        }
    }

    private void handleBatchUpdateResult(@NotNull List<UpdateResult<K>> results) {
        ValueFormatter formatter = TopValueFormatterProvider.isAvailable()
                ? TopValueFormatterProvider.getInstance().resolve(this)
                : null;

        
        
        
        List<Pending<K>> pending = new ArrayList<>(results.size());
        for (UpdateResult<K> result : results) {
            if (!result.isSuccess() || result.getNewValue() == null) continue;
            if (result.getNewValue() == 0.0 && !config.isAllowZeroValues()) continue;

            K identifier = result.getIdentifier();
            Optional<TopEntry<K>> oldEntry = cache.getEntryByIdentifier(identifier);
            Double  oldValue    = oldEntry.map(TopEntry::getValue).orElse(null);
            Integer oldPosition = oldEntry.map(TopEntry::getPosition).orElse(null);

            String displayName = result.getDisplayName() != null
                    ? result.getDisplayName()
                    : oldEntry.map(TopEntry::getDisplayName).orElse(null);
            if (displayName == null) continue;

            pending.add(new Pending<>(identifier, displayName, result.getNewValue(), oldValue, oldPosition));
        }
        if (pending.isEmpty()) return;

        
        for (Pending<K> p : pending) {
            List<K> staleIds = cache.removeByDisplayName(p.displayName, p.identifier);
            for (K staleId : staleIds) {
                CompletableFuture.runAsync(() -> storage.remove(id, staleId), DatabaseExecutors.DB_EXECUTOR);
            }
            cache.updateEntry(p.identifier, p.displayName, p.newValue, config.getSize());
        }

        
        
        List<EventEntry<K>> events = new ArrayList<>(pending.size());
        for (Pending<K> p : pending) {
            int finalPosRaw = cache.getPosition(p.identifier);
            Integer newPosition = finalPosRaw == -1 ? null : finalPosRaw;

            if (p.oldPosition == null && newPosition == null) continue;
            boolean positionChanged = !Objects.equals(p.oldPosition, newPosition);
            if (!positionChanged) {
                Debug.log("[{}] No change for {} (pos {}, value {}), skipping event",
                        id, p.displayName, newPosition, p.newValue);
                continue;
            }

            String fmtOld = (formatter != null && p.oldValue != null) ? formatter.format(p.oldValue) : null;
            String fmtNew = (formatter != null)                       ? formatter.format(p.newValue) : null;

            Debug.log("[{}] Position update for {}: pos {} -> {}, value {} -> {} (fmt {} -> {})",
                    id, p.displayName, p.oldPosition, newPosition, p.oldValue, p.newValue, fmtOld, fmtNew);

            events.add(new EventEntry<>(p.identifier, p.displayName, p.oldValue, p.newValue,
                                       p.oldPosition, newPosition, fmtOld, fmtNew));
        }

        if (events.isEmpty()) return;


        Bukkit.getScheduler().runTask(plugin, () -> {
            for (EventEntry<K> e : events) {
                TopEventDispatcher.firePositionUpdate(
                        id, e.identifier, e.displayName, e.oldValue, e.newValue,
                        e.oldPosition, e.newPosition, e.fmtOld, e.fmtNew
                );
            }
        });
    }

    private record Pending<K>(K identifier, String displayName, double newValue,
                              Double oldValue, Integer oldPosition) {}

    private static final class EventEntry<V> {
        final V       identifier;
        final String  displayName;
        final Double  oldValue;
        final double  newValue;
        final Integer oldPosition;
        final Integer newPosition;
        final String  fmtOld;
        final String  fmtNew;

        EventEntry(V identifier, String displayName, Double oldValue, double newValue,
                   Integer oldPosition, Integer newPosition, String fmtOld, String fmtNew) {
            this.identifier  = identifier;
            this.displayName = displayName;
            this.oldValue    = oldValue;
            this.newValue    = newValue;
            this.oldPosition = oldPosition;
            this.newPosition = newPosition;
            this.fmtOld      = fmtOld;
            this.fmtNew      = fmtNew;
        }
    }

    @Override @NotNull public String getId()        { return id; }
    @Override @NotNull public TopConfig getConfig() { return config; }

    @Override @NotNull
    public List<TopEntry<K>> getEntries() { return cache.getEntriesCopy(); }

    @Override @NotNull
    public Optional<TopEntry<K>> getEntry(int position) {
        if (position < 1) return Optional.empty();
        return cache.getEntryAt(position);
    }

    @Override
    public int getPosition(@NotNull K identifier) {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        return cache.getPosition(identifier);
    }

    @Override
    public boolean isInTop(@NotNull K identifier) { return getPosition(identifier) != -1; }

    @Override @NotNull public Optional<Double> getMinValue() { return cache.getMinValue(); }
    @Override @NotNull public Optional<Double> getMaxValue() { return cache.getMaxValue(); }
    @Override public int getCurrentSize()                    { return cache.size(); }

    @Override
    public void markDirty(@NotNull K identifier, @NotNull String reason) {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        Objects.requireNonNull(reason,     "reason cannot be null");
        queue.enqueue(identifier, Priority.CRITICAL, reason);
    }

    @Override
    public void enqueue(@NotNull Collection<K> identifiers,
                        @NotNull Priority priority,
                        @NotNull String reason) {
        Objects.requireNonNull(identifiers, "identifiers cannot be null");
        Objects.requireNonNull(priority,    "priority cannot be null");
        Objects.requireNonNull(reason,      "reason cannot be null");
        queue.enqueueAll(identifiers, priority, reason);
    }

    @Override @NotNull public ValueProvider<K>   getValueProvider() { return valueProvider; }
    @Override @NotNull public ProcessingQueue<K> getQueue()         { return queue; }
    @Override @NotNull public TopProcessor<K>    getProcessor()     { return processor; }

    protected void removeFromTop(@NotNull K identifier) {
        cache.removeEntry(identifier);
        CompletableFuture.runAsync(() -> storage.remove(id, identifier), DatabaseExecutors.DB_EXECUTOR);
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
        return "DefaultTop{id='" + id + "', size=" + getCurrentSize() + "/" + config.getSize()
                + ", queue=" + queue.size() + '}';
    }
}
