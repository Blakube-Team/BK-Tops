package com.blakube.bktops.plugin.schedule;

import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.plugin.cache.PlayerNameCache;
import com.blakube.bktops.plugin.provider.TimedValueProvider;
import com.blakube.bktops.plugin.top.DefaultTimedTop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ProcessorScheduler {

    private final JavaPlugin plugin;
    private final TopRegistry<UUID> registry;
    private final List<BukkitTask> tasks;

    
    private final Map<String, Integer> rotativeOffset = new ConcurrentHashMap<>();

    public ProcessorScheduler(@NotNull JavaPlugin plugin, @NotNull TopRegistry<UUID> registry) {
        this.plugin   = plugin;
        this.registry = registry;
        this.tasks    = new ArrayList<>();
    }

    public void start() {
        plugin.getLogger().info("Initializing processor scheduler...");

        List<CompletableFuture<Void>> futures        = new ArrayList<>();
        List<TimedValueProvider<UUID>> timedProviders = new ArrayList<>();

        for (Top<UUID> top : registry.getAll()) {
            if (top instanceof DefaultTimedTop<UUID> timedTop
                    && timedTop.getValueProvider() instanceof TimedValueProvider<UUID> provider) {
                timedProviders.add(provider);
                futures.add(provider.getInitializationFuture());
            }
        }

        if (!futures.isEmpty()) {
            plugin.getLogger().info("Waiting for " + futures.size() + " timed providers to load async...");
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .handle((unused, ex) -> {
                        if (ex != null) {
                            plugin.getLogger().severe("Error initializing providers: " + ex.getMessage());
                        }
                        
                        
                        if (plugin.isEnabled()) {
                            Bukkit.getScheduler().runTask(plugin, () -> startTasks(timedProviders));
                        }
                        return null;
                    });
        } else {
            startTasks(Collections.emptyList());
        }
    }

    private void startTasks(List<TimedValueProvider<UUID>> timedProviders) {
        plugin.getLogger().info("All providers initialized! Starting tasks...");

        
        
        List<UUID> onlineUUIDs = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerNameCache.put(player.getUniqueId(), player.getName());
            onlineUUIDs.add(player.getUniqueId());
        }

        for (Top<UUID> top : registry.getAll()) {
            if (!onlineUUIDs.isEmpty()) {
                top.enqueue(onlineUUIDs, Priority.HIGH, "online_at_startup");
            }

            if (top.getValueProvider() instanceof TimedValueProvider<UUID> provider) {
                Map<UUID, Double> snapshots = provider.getSnapshotCache();
                if (!snapshots.isEmpty()) {
                    top.enqueue(new ArrayList<>(snapshots.keySet()), Priority.HIGH, "server_startup");
                }
            }
        }

        if (!onlineUUIDs.isEmpty()) {
            plugin.getLogger().info("Enqueued " + onlineUUIDs.size() + " online player(s) for startup processing.");
        }

        startMainProcessor();
        startOnlineQueueTask();
        startRotativeQueueTask();
        startTimedResetTask();

        plugin.getLogger().info(tasks.size() + " tasks started successfully.");
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        plugin.getLogger().info("Scheduler stopped.");
    }

    private void startMainProcessor() {
        for (Top<UUID> top : registry.getAll()) {
            int period = Math.max(1, top.getConfig().getTickDelay());
            
            
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!top.getProcessor().isEnabled()) return;
                    top.getProcessor().processBatch(top.getConfig().getBatchSize());
                }
            }.runTaskTimerAsynchronously(plugin, 0L, period);
            tasks.add(task);
        }
    }

    private void startOnlineQueueTask() {
        for (Top<UUID> top : registry.getAll()) {
            if (!top.getConfig().isEnableOnlineQueue()) continue;
            int period = Math.max(1, top.getConfig().getOnlineQueueInterval());
            
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    List<UUID> onlineUUIDs = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getUniqueId)
                            .toList();
                    if (onlineUUIDs.isEmpty()) return;
                    com.blakube.bktops.plugin.debug.Debug.log("[{}] Enqueuing {} online player(s) (online_periodic)",
                            top.getId(), onlineUUIDs.size());
                    top.enqueue(onlineUUIDs, Priority.HIGH, "online_periodic");
                }
            }.runTaskTimer(plugin, 20L, period);
            tasks.add(task);
        }
    }

    private void startRotativeQueueTask() {
        for (Top<UUID> top : registry.getAll()) {
            if (!top.getConfig().isEnableRotativeQueue()) continue;
            rotativeOffset.put(top.getId(), 0);
            int period = Math.max(1, top.getConfig().getRotativeQueueInterval());
            
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    List<?> entries = top.getEntries();
                    if (entries.isEmpty()) return;

                    String topId  = top.getId();
                    int maxSize   = Math.min(top.getConfig().getRotativeQueueSize(), entries.size());
                    int offset    = rotativeOffset.getOrDefault(topId, 0);
                    int batchSize = top.getConfig().getBatchSize();

                    List<UUID> toEnqueue = new ArrayList<>(batchSize);
                    for (int i = 0; i < batchSize && offset < maxSize; i++, offset++) {
                        @SuppressWarnings("unchecked")
                        com.blakube.bktops.api.top.TopEntry<UUID> entry =
                                (com.blakube.bktops.api.top.TopEntry<UUID>) entries.get(offset);
                        toEnqueue.add(entry.getIdentifier());
                    }
                    if (!toEnqueue.isEmpty()) {
                        top.enqueue(toEnqueue, Priority.MEDIUM, "rotative_check");
                    }
                    rotativeOffset.put(topId, offset >= maxSize ? 0 : offset);
                }
            }.runTaskTimerAsynchronously(plugin, 40L, period);
            tasks.add(task);
        }
    }

    private void startTimedResetTask() {
        
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Top<UUID> top : registry.getAll()) {
                    if (!(top instanceof DefaultTimedTop<UUID> timedTop)) continue;
                    if (!timedTop.shouldReset()) continue;

                    plugin.getLogger().info("Resetting top: " + timedTop.getId());
                    timedTop.resetAsync()
                            .thenRun(() -> plugin.getLogger().info("Reset completed: " + timedTop.getId()))
                            .exceptionally(ex -> {
                                plugin.getLogger().severe("Error resetting " + timedTop.getId()
                                        + ": " + ex.getMessage());
                                ex.printStackTrace();
                                return null;
                            });
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L);

        tasks.add(task);
    }
}
