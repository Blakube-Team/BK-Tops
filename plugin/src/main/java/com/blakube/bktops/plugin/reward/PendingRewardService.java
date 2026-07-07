package com.blakube.bktops.plugin.reward;

import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.plugin.formatter.TopValueFormatterProvider;
import com.blakube.bktops.plugin.notification.EventContext;
import com.blakube.bktops.plugin.notification.NotificationService;
import com.blakube.bktops.plugin.reward.item.RTagItemSerializer;
import com.blakube.bktops.plugin.reward.storage.PendingRewardDAO;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingRewardService {

    private final Plugin plugin;
    private final PendingRewardDAO dao;
    private final RTagItemSerializer itemSerializer;
    private final TopRegistry<UUID> registry;
    private final NotificationService notifications;
    private final boolean placeholderAPIAvailable;
    private final Map<UUID, List<PendingReward>> pendingByPlayer = new ConcurrentHashMap<>();
    private final Set<String> delivering = ConcurrentHashMap.newKeySet();

    public PendingRewardService(@NotNull Plugin plugin,
                                @NotNull PendingRewardDAO dao,
                                @NotNull RTagItemSerializer itemSerializer,
                                @NotNull TopRegistry<UUID> registry,
                                @NotNull NotificationService notifications) {
        this.plugin = plugin;
        this.dao = dao;
        this.itemSerializer = itemSerializer;
        this.registry = registry;
        this.notifications = notifications;
        this.placeholderAPIAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public void loadPendingFromStorage() {
        dao.initialize();
        pendingByPlayer.clear();
        for (PendingReward reward : dao.loadUndelivered()) {
            addToMemory(reward);
        }
        plugin.getLogger().info("Loaded pending top rewards: " + pendingByPlayer.values().stream().mapToInt(List::size).sum());
    }

    public void enqueue(@NotNull Collection<PendingReward> rewards) {
        if (rewards.isEmpty()) return;

        List<PendingReward> snapshot = List.copyOf(rewards);
        CompletableFuture
                .runAsync(() -> dao.saveBatch(snapshot), DatabaseExecutors.DB_EXECUTOR)
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Set<UUID> players = new HashSet<>();
                    for (PendingReward reward : snapshot) {
                        addToMemory(reward);
                        players.add(reward.playerUuid());
                    }
                    for (UUID uuid : players) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            deliverPending(player);
                        }
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Could not persist pending top rewards: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });
    }

    public void deliverPending(@NotNull Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> deliverPending(player));
            return;
        }

        List<PendingReward> rewards = pendingByPlayer.get(player.getUniqueId());
        if (rewards == null || rewards.isEmpty()) return;

        List<PendingReward> ordered = rewards.stream()
                .filter(reward -> delivering.add(reward.id()))
                .sorted(Comparator.comparing(PendingReward::createdAt))
                .toList();
        if (ordered.isEmpty()) return;

        List<String> deliveredIds = new ArrayList<>();
        Set<DeliveryNotice> notices = new HashSet<>();

        for (PendingReward reward : ordered) {
            try {
                deliver(player, reward);
                deliveredIds.add(reward.id());
                notices.add(new DeliveryNotice(reward.topId(), reward.position(), reward.score()));
            } catch (Throwable t) {
                plugin.getLogger().severe("Could not deliver top reward " + reward.id()
                        + " to " + player.getName() + ": " + t.getMessage());
            } finally {
                delivering.remove(reward.id());
            }
        }

        if (deliveredIds.isEmpty()) return;

        removeFromMemory(player.getUniqueId(), deliveredIds);
        long deliveredAt = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> dao.markDelivered(deliveredIds, deliveredAt), DatabaseExecutors.DB_EXECUTOR)
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Could not mark top rewards as delivered: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });

        for (DeliveryNotice notice : notices) {
            notifications.notifyRewardDelivered(toContext(player, notice));
        }
    }

    public void purgeDeliveredOlderThan(@NotNull Duration age) {
        long before = System.currentTimeMillis() - age.toMillis();
        CompletableFuture.runAsync(() -> dao.purgeDeliveredBefore(before), DatabaseExecutors.DB_EXECUTOR);
    }

    private void deliver(@NotNull Player player, @NotNull PendingReward reward) {
        switch (reward.actionType()) {
            case ITEM -> deliverItem(player, reward);
            case COMMAND -> performCommand(player, reward);
        }
    }

    private void deliverItem(@NotNull Player player, @NotNull PendingReward reward) {
        ItemStack item = itemSerializer.deserialize(reward.payload()).clone();
        item.setAmount(reward.amount());
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack leftoverItem : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
        }
    }

    private void performCommand(@NotNull Player player, @NotNull PendingReward reward) {
        String command = resolveRewardText(player, reward, reward.payload());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @NotNull
    private EventContext toContext(@NotNull Player player, @NotNull DeliveryNotice notice) {
        String topName = registry.get(notice.topId())
                .map(Top::getConfig)
                .map(config -> config.getDisplayName() != null ? config.getDisplayName() : notice.topId())
                .orElse(notice.topId());
        String score = registry.get(notice.topId())
                .map(top -> TopValueFormatterProvider.isAvailable()
                        ? TopValueFormatterProvider.getInstance().resolve(top).format(notice.score())
                        : String.valueOf(notice.score()))
                .orElse(String.valueOf(notice.score()));

        return EventContext.rewardDelivered(
                player.getName(),
                String.valueOf(notice.position()),
                notice.topId(),
                topName,
                score
        );
    }

    @NotNull
    private String resolveRewardText(@NotNull Player player, @NotNull PendingReward reward, @NotNull String raw) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(reward.playerUuid());
        String name = player.getName();
        String text = raw
                .replace("{player}", name)
                .replace("{uuid}", reward.playerUuid().toString())
                .replace("{top_id}", reward.topId())
                .replace("{position}", String.valueOf(reward.position()))
                .replace("{score}", String.valueOf(reward.score()));

        if (placeholderAPIAvailable) {
            try {
                text = PlaceholderAPI.setPlaceholders(offline, text);
            } catch (Throwable ignored) {
            }
        }
        return text.startsWith("/") ? text.substring(1) : text;
    }

    private void addToMemory(@NotNull PendingReward reward) {
        pendingByPlayer.compute(reward.playerUuid(), (uuid, current) -> {
            List<PendingReward> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            boolean exists = next.stream().anyMatch(existing -> Objects.equals(existing.id(), reward.id()));
            if (!exists) next.add(reward);
            return next;
        });
    }

    private void removeFromMemory(@NotNull UUID uuid, @NotNull Collection<String> deliveredIds) {
        pendingByPlayer.computeIfPresent(uuid, (ignored, current) -> {
            List<PendingReward> remaining = current.stream()
                    .filter(reward -> !deliveredIds.contains(reward.id()))
                    .toList();
            return remaining.isEmpty() ? null : new ArrayList<>(remaining);
        });
    }

    private record DeliveryNotice(@NotNull String topId, int position, double score) {
    }
}
