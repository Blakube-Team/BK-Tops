package com.blakube.bktops.plugin.notification;

import com.blakube.bktops.api.config.ConfigContainer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;







public final class DiscordWebhookSender {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Plugin plugin;
    private final boolean placeholderAPIAvailable;

    private volatile ConfigContainer config;

    public DiscordWebhookSender(@NotNull Plugin plugin, @NotNull ConfigContainer config) {
        this.plugin = plugin;
        this.config = config;
        this.placeholderAPIAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    
    public void reload(@NotNull ConfigContainer config) {
        this.config = config;
    }

    public boolean isEnabled() {
        String url = config.getString("webhook_url", "");
        return config.getBoolean("enabled", false)
                && url != null && !url.isEmpty()
                && !url.contains("ID/TOKEN");
    }

    
    
    

    public void notifyTimedTopReset(@NotNull EventContext ctx) {
        if (!isEnabled()) return;
        ConfigurationSection sec = config.getConfigurationSection("events.timed-top-reset");
        if (sec == null || !sec.getBoolean("enabled", true)) return;
        sendFromSection(sec, ctx);
    }

    public void notifyTopPositionUpdate(@NotNull EventContext ctx) {
        if (!isEnabled()) return;
        ConfigurationSection root = config.getConfigurationSection("events.top-position-update");
        if (root == null || !root.getBoolean("enabled", true)) return;
        ConfigurationSection c = NotificationCases.firstMatching(root.getConfigurationSection("cases"), ctx);
        if (c != null) sendFromSection(c, ctx);
    }

    
    
    

    private void sendFromSection(@NotNull ConfigurationSection sec, @NotNull EventContext ctx) {
        Player papi = Bukkit.getPlayerExact(ctx.getPlayer());

        List<String> resolved = new ArrayList<>();
        for (String line : sec.getStringList("content")) resolved.add(resolve(line, papi, ctx));
        String content  = String.join("\n", resolved);
        String username = resolve(sec.getString("username", config.getString("username", plugin.getName())), papi, ctx);
        String avatar   = resolve(sec.getString("avatar_url", config.getString("avatar_url", "")), papi, ctx);

        ConfigurationSection embed = sec.getConfigurationSection("embed");
        post(buildPayload(content, username, avatar, embed, papi, ctx));
    }

    private String resolve(@Nullable String raw, @Nullable Player player, @NotNull EventContext ctx) {
        String out = ctx.resolve(raw);
        if (player != null && placeholderAPIAvailable) {
            out = PlaceholderAPI.setPlaceholders(player, out);
        }
        return out;
    }

    
    
    

    private String buildPayload(String content, String username, String avatarUrl,
                                @Nullable ConfigurationSection embedSec, @Nullable Player player,
                                @NotNull EventContext ctx) {
        List<String> fields = new ArrayList<>();
        if (username  != null && !username.isEmpty())  fields.add("\"username\":"   + json(username));
        if (avatarUrl != null && !avatarUrl.isEmpty()) fields.add("\"avatar_url\":" + json(avatarUrl));
        if (content   != null && !content.isEmpty())   fields.add("\"content\":"    + json(content));

        if (embedSec != null && embedSec.getBoolean("enabled", false)) {
            String title       = resolve(embedSec.getString("title", ""),       player, ctx);
            String description = resolve(embedSec.getString("description", ""), player, ctx);
            int color          = embedSec.getInt("color", 0x2f3136);

            List<String> ef = new ArrayList<>();
            if (!title.isEmpty())       ef.add("\"title\":"       + json(title));
            if (!description.isEmpty()) ef.add("\"description\":" + json(description));
            ef.add("\"color\":" + color);

            fields.add("\"embeds\":[{" + String.join(",", ef) + "}]");
        }

        return "{" + String.join(",", fields) + "}";
    }

    private static String json(@NotNull String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    
    
    

    private void post(@NotNull String json) {
        String url = config.getString("webhook_url", "");
        if (url == null || url.isEmpty()) return;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Discord webhook failed: " + ex.getMessage());
                        return null;
                    });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Discord webhook error: " + ex.getMessage());
        }
    }
}
