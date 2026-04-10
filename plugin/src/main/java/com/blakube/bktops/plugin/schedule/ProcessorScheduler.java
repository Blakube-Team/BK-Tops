package com.blakube.bktops.plugin.schedule;

import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.top.Top;
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

public final class ProcessorScheduler {

    private final JavaPlugin plugin;
    private final TopRegistry<UUID> registry;
    private final List<BukkitTask> tasks;

    private final Map<String, Long> nextProcessAtMs      = new HashMap<>();
    private final Map<String, Long> nextOnlineEnqueueAtMs = new HashMap<>();
    private final Map<String, Integer> rotativeOffset    = new HashMap<>();

    public ProcessorScheduler(@NotNull JavaPlugin plugin, @NotNull TopRegistry<UUID> registry) {
        this.plugin   = plugin;
        this.registry = registry;
        this.tasks    = new ArrayList<>();
    }

    public void start() {
        plugin.getLogger().info("Initializing processor scheduler...");

        List<CompletableFuture<Void>> futures       = new ArrayList<>();
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
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> startTasks(timedProviders)))
                    .exceptionally(ex -> {
                        plugin.getLogger().severe("Error initializing providers: " + ex.getMessage());
                        return null;
                    });
        } else {
            startTasks(Collections.emptyList());
        }
    }

    private void startTasks(List<TimedValueProvider<UUID>> timedProviders) {
        plugin.getLogger().info("All providers initialized! Starting tasks...");

        for (Top<UUID> top : registry.getAll()) {
            if (top.getValueProvider() instanceof TimedValueProvider<UUID> provider) {
                Map<UUID, Double> snapshots = provider.getSnapshotCache();
                if (!snapshots.isEmpty()) {
                    top.enqueue(new ArrayList<>(snapshots.keySet()), Priority.HIGH, "server_startup");
                }
            }
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
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Top<UUID> top : registry.getAll()) {
                    if (!top.getProcessor().isEnabled()) continue;

                    int tickDelay = top.getConfig().getTickDelay();
                    if (tickDelay <= 1) {
                        top.getProcessor().processBatch(top.getConfig().getBatchSize());
                        continue;
                    }

                    long now     = System.currentTimeMillis();
                    long periodMs = tickDelay * 50L;
                    String id    = top.getId();
                    if (now >= nextProcessAtMs.getOrDefault(id, 0L)) {
                        top.getProcessor().processBatch(top.getConfig().getBatchSize());
                        nextProcessAtMs.put(id, now + periodMs);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        tasks.add(task);
    }

    private void startOnlineQueueTask() {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                List<UUID> onlineUUIDs = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getUniqueId)
                        .toList();

                if (onlineUUIDs.isEmpty()) return;

                long now = System.currentTimeMillis();
                for (Top<UUID> top : registry.getAll()) {
                    if (!top.getConfig().isEnableOnlineQueue()) continue;

                    String id      = top.getId();
                    long periodMs  = Math.max(1, top.getConfig().getOnlineQueueInterval()) * 50L;
                    if (now >= nextOnlineEnqueueAtMs.getOrDefault(id, 0L)) {
                        top.enqueue(onlineUUIDs, Priority.HIGH, "online_periodic");
                        nextOnlineEnqueueAtMs.put(id, now + periodMs);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        tasks.add(task);
    }

    private void startRotativeQueueTask() {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Top<UUID> top : registry.getAll()) {
                    if (!top.getConfig().isEnableRotativeQueue()) continue;

                    var entries = top.getEntries();
                    if (entries.isEmpty()) continue;

                    String id      = top.getId();
                    int maxSize    = Math.min(top.getConfig().getRotativeQueueSize(), entries.size());
                    int offset     = rotativeOffset.getOrDefault(id, 0);
                    int batchSize  = 10;

                    for (int i = 0; i < batchSize && offset < maxSize; i++, offset++) {
                        top.enqueue(
                                List.of(entries.get(offset).getIdentifier()),
                                Priority.MEDIUM,
                                "rotative_check"
                        );
                    }

                    rotativeOffset.put(id, offset >= maxSize ? 0 : offset);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);

        tasks.add(task);
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
        }.runTaskTimer(plugin, 1200L, 1200L);

        tasks.add(task);
    }
}
