package com.blakube.bktops.plugin.schedule;

import com.blakube.bktops.api.queue.Priority;
import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.timed.TimedTop;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class ProcessorScheduler {

    private final JavaPlugin plugin;
    private final TopRegistry<UUID> registry;
    private final List<BukkitTask> tasks;
    private final Map<String, Long> nextProcessAtMs = new HashMap<>();
    private final Map<String, Long> nextOnlineEnqueueAtMs = new HashMap<>();

    public ProcessorScheduler(@NotNull JavaPlugin plugin, @NotNull TopRegistry<UUID> registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.tasks = new ArrayList<>();
    }

    public void start() {
        plugin.getLogger().info("Initializing processor scheduler...");

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<TimedValueProvider<UUID>> timedProviders = new ArrayList<>();

        for (Top<UUID> top : registry.getAll()) {
            if (top instanceof DefaultTimedTop) {
                var timedTop = (DefaultTimedTop<UUID>) top;
                if (timedTop.getValueProvider() instanceof TimedValueProvider) {
                    var provider = (TimedValueProvider<UUID>) timedTop.getValueProvider();
                    timedProviders.add(provider);
                    futures.add(provider.getInitializationFuture());
                }
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
            if (top.getValueProvider() instanceof TimedValueProvider) {
                var provider = (TimedValueProvider<UUID>) top.getValueProvider();
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
                    if (!top.getProcessor().isEnabled()) {
                        continue;
                    }
                    int tickDelay = top.getConfig().getTickDelay();
                    if (tickDelay <= 1) {
                        top.getProcessor().processBatch(top.getConfig().getBatchSize());
                        continue;
                    }

                    long now = System.currentTimeMillis();
                    long periodMs = tickDelay * 50L;
                    String id = top.getId();
                    long nextDue = nextProcessAtMs.getOrDefault(id, 0L);
                    if (now >= nextDue) {
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
                        .collect(Collectors.toList());

                if (onlineUUIDs.isEmpty()) {
                    return;
                }

                for (Top<UUID> top : registry.getAll()) {
                    if (top.getConfig().isEnableOnlineQueue()) {
                        String id = top.getId();
                        int intervalTicks = top.getConfig().getOnlineQueueInterval();
                        long periodMs = Math.max(1, intervalTicks) * 50L;
                        long now = System.currentTimeMillis();
                        long nextDue = nextOnlineEnqueueAtMs.getOrDefault(id, 0L);
                        if (now >= nextDue) {
                            top.enqueue(onlineUUIDs, Priority.HIGH, "online_periodic");
                            nextOnlineEnqueueAtMs.put(id, now + periodMs);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        tasks.add(task);
    }

    private void startRotativeQueueTask() {
        BukkitTask task = new BukkitRunnable() {
            private int offset = 0;

            @Override
            public void run() {
                for (Top<UUID> top : registry.getAll()) {
                    if (!top.getConfig().isEnableRotativeQueue()) {
                        continue;
                    }

                    var entries = top.getEntries();
                    if (entries.isEmpty()) {
                        continue;
                    }

                    int maxSize = Math.min(
                            top.getConfig().getRotativeQueueSize(),
                            entries.size()
                    );

                    int batchSize = 10;
                    for (int i = 0; i < batchSize && offset < maxSize; i++) {
                        var entry = entries.get(offset);
                        top.enqueue(
                                List.of(entry.getIdentifier()),
                                Priority.MEDIUM,
                                "rotative_check"
                        );
                        offset++;
                    }

                    if (offset >= maxSize) {
                        offset = 0;
                    }
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
                    if (top instanceof TimedTop) {
                        TimedTop<UUID> timedTop = (TimedTop<UUID>) top;

                        if (timedTop.shouldReset()) {
                            plugin.getLogger().info("Resetting top: " + timedTop.getId());

                            if (timedTop instanceof com.blakube.bktops.plugin.top.DefaultTimedTop) {
                                var impl = (com.blakube.bktops.plugin.top.DefaultTimedTop<UUID>) timedTop;

                                impl.resetAsync()
                                        .thenRun(() -> {
                                            plugin.getLogger().info("Reset completed: " + timedTop.getId());
                                        })
                                        .exceptionally(ex -> {
                                            plugin.getLogger().severe("Error resetting " + timedTop.getId() + ": " + ex.getMessage());
                                            ex.printStackTrace();
                                            return null;
                                        });
                            } else {
                                timedTop.reset();
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L);

        tasks.add(task);
    }
}