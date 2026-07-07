package com.blakube.bktops.plugin.notification;

import com.blakube.bktops.api.config.ConfigContainer;
import com.blakube.bktops.plugin.debug.Debug;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;









public final class NotificationService {

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacy;
    private final boolean placeholderAPIAvailable;
    private final DiscordWebhookSender discord;

    private volatile ConfigContainer config;

    public NotificationService(@NotNull ConfigContainer config, @NotNull DiscordWebhookSender discord) {
        this.config  = config;
        this.discord = discord;
        this.miniMessage = MiniMessage.builder()
                .postProcessor(c -> c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                .build();
        this.legacy = LegacyComponentSerializer.legacyAmpersand();
        this.placeholderAPIAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    
    public void reload(@NotNull ConfigContainer config) {
        this.config = config;
    }

    
    
    

    public void notifyTimedTopReset(@NotNull EventContext ctx) {
        if (config.getBoolean("enabled", true)) {
            ConfigurationSection sec = config.getConfigurationSection("events.timed-top-reset");
            if (sec != null && sec.getBoolean("enabled", true)) {
                sendAll(sec, resolveAudience(sec.getString("audience", "all"), ctx), ctx);
            }
        }
        discord.notifyTimedTopReset(ctx);
    }

    public void notifyTopPositionUpdate(@NotNull EventContext ctx) {
        if (config.getBoolean("enabled", true)) {
            ConfigurationSection root = config.getConfigurationSection("events.top-position-update");
            if (root != null && root.getBoolean("enabled", true)) {
                ConfigurationSection c = NotificationCases.firstMatching(root.getConfigurationSection("cases"), ctx);
                if (c != null) {
                    sendAll(c, resolveAudience(c.getString("audience", "player"), ctx), ctx);
                }
            }
        }
        discord.notifyTopPositionUpdate(ctx);
    }

    public void notifyRewardDelivered(@NotNull EventContext ctx) {
        if (!config.getBoolean("enabled", true)) return;

        ConfigurationSection sec = config.getConfigurationSection("events.reward-delivered");
        if (sec != null && sec.getBoolean("enabled", true)) {
            sendAll(sec, resolveAudience(sec.getString("audience", "player"), ctx), ctx);
        }
    }

    
    
    

    private void sendAll(@NotNull ConfigurationSection sec, @NotNull Collection<Player> audience,
                         @NotNull EventContext ctx) {
        if (audience.isEmpty()) return;
        Player papi = Bukkit.getPlayerExact(ctx.getPlayer());

        ConfigurationSection chat = sec.getConfigurationSection("chat");
        if (chat != null && chat.getBoolean("enabled", false)) {
            for (String line : chat.getStringList("lines")) {
                Component msg = render(papi, line, ctx);
                for (Player p : audience) p.sendMessage(msg);
            }
        }

        ConfigurationSection title = sec.getConfigurationSection("title");
        if (title != null && title.getBoolean("enabled", false)) {
            Component t  = render(papi, title.getString("title", ""), ctx);
            Component st = render(papi, title.getString("subtitle", ""), ctx);
            Title.Times times = Title.Times.times(
                    ticks(title.getInt("fade-in",  10)),
                    ticks(title.getInt("stay",     40)),
                    ticks(title.getInt("fade-out", 10))
            );
            Title fullTitle = Title.title(t, st, times);
            for (Player p : audience) p.showTitle(fullTitle);
        }

        ConfigurationSection ab = sec.getConfigurationSection("actionbar");
        if (ab != null && ab.getBoolean("enabled", false)) {
            Component msg = render(papi, ab.getString("text", ""), ctx);
            for (Player p : audience) p.sendActionBar(msg);
        }

        ConfigurationSection sound = sec.getConfigurationSection("sound");
        if (sound != null && sound.getBoolean("enabled", false)) {
            Sound s = resolveSound(sound.getString("name", ""));
            if (s != null) {
                float volume = (float) sound.getDouble("volume", 1.0);
                float pitch  = (float) sound.getDouble("pitch",  1.0);
                for (Player p : audience) p.playSound(p.getLocation(), s, volume, pitch);
            }
        }
    }

    
    
    

    private Collection<Player> resolveAudience(@NotNull String audience, @NotNull EventContext ctx) {
        return switch (audience.toLowerCase()) {
            case "player" -> {
                Player p = Bukkit.getPlayerExact(ctx.getPlayer());
                yield p != null ? Collections.singletonList(p) : Collections.emptyList();
            }
            case "world" -> {
                Player p = Bukkit.getPlayerExact(ctx.getPlayer());
                yield p != null ? p.getWorld().getPlayers() : Collections.emptyList();
            }
            default -> new ArrayList<>(Bukkit.getOnlinePlayers());
        };
    }

    
    
    

    private Component render(@Nullable Player player, @Nullable String raw, @NotNull EventContext ctx) {
        String text = ctx.resolve(raw);
        if (placeholderAPIAvailable && player != null) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        if (text.indexOf('<') != -1 && text.indexOf('>') != -1) {
            return miniMessage.deserialize(text);
        }
        return legacy.deserialize(text);
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * 50L);
    }

    @Nullable
    private static Sound resolveSound(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            Debug.log("Unknown notification sound '{}', skipping", name);
            return null;
        }
    }
}
