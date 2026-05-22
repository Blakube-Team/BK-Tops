package com.blakube.bktops.plugin.hook.placeholder;

import com.blakube.bktops.api.config.ConfigType;
import com.blakube.bktops.plugin.formatter.NumberFormatter;
import com.blakube.bktops.plugin.formatter.NumberValueFormatter;
import com.blakube.bktops.plugin.formatter.TimeValueFormatter;
import com.blakube.bktops.plugin.formatter.ValueFormatter;
import com.blakube.bktops.plugin.service.config.ConfigService;
import com.blakube.bktops.plugin.service.time.ObtainTimeService;
import com.blakube.bktops.plugin.service.time.TimeFormatService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.top.TopEntry;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private static final ValueFormatter DEFAULT_FORMATTER = new NumberValueFormatter(null);

    private final ConfigService configService;
    private final TimeFormatService timeFormatService;
    private final ObtainTimeService obtainTimeService = new ObtainTimeService();

    private Map<String, ValueFormatter> modeFormatters;
    private final Map<String, ValueFormatter> topFormatterCache = new ConcurrentHashMap<>();

    public PlaceholderAPIHook(ConfigService configService) {
        this.configService = configService;
        this.timeFormatService = new TimeFormatService(configService);
        this.modeFormatters = buildModeFormatters();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bktops";
    }

    @Override
    public @NotNull String getAuthor() {
        return "hhitt";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (identifier.startsWith("team_")) {
            identifier = identifier.substring("team_".length());
        }

        if (identifier.startsWith("time_")) {
            String topId = identifier.substring("time_".length());
            if (topId.isEmpty()) {
                return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");
            }

            Optional<Duration> until = obtainTimeService.getTimeUntilReset(topId);
            return until.map(timeFormatService::formatTwoUnits)
                    .orElseGet(() -> configService.provide(ConfigType.LANG)
                            .getString("invalid-placeholder.top-id", "Unknown topId!"));
        }

        if (identifier.startsWith("myposition_")) {
            if (player == null) return notInTop();
            String topId = identifier.substring("myposition_".length());
            return handleMyPositionPlaceholder(player, topId);
        }

        if (identifier.startsWith("displayname_")) {
            String topId = identifier.substring("displayname_".length());
            return handleDisplayNamePlaceholder(topId);
        }

        if (identifier.startsWith("distance_above_")) {
            String topId = identifier.substring("distance_above_".length());
            return handleDistanceAbovePlaceholder(player, topId);
        }

        if (identifier.startsWith("distance_below_")) {
            String topId = identifier.substring("distance_below_".length());
            return handleDistanceBelowPlaceholder(player, topId);
        }

        if (identifier.startsWith("above_name_")) {
            String topId = identifier.substring("above_name_".length());
            return handleAboveNamePlaceholder(player, topId);
        }

        if (identifier.startsWith("below_name_")) {
            String topId = identifier.substring("below_name_".length());
            return handleBelowNamePlaceholder(player, topId);
        }

        String[] parts = identifier.split("_");

        if (parts.length < 3) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.format", "Invalid format!");
        }

        String typeStr = parts[0];

        ValueFormatter inlineFormatter = null;
        if (typeStr.contains(":")) {
            String[] typeParts = typeStr.split(":", 2);
            typeStr = typeParts[0];
            inlineFormatter = modeFormatters.get(typeParts[1].toUpperCase());
        }

        
        int topIdStart = 1;
        if (inlineFormatter == null && parts.length >= 4) {
            ValueFormatter modeMatch = modeFormatters.get(parts[1].toUpperCase());
            if (modeMatch != null) {
                inlineFormatter = modeMatch;
                topIdStart = 2;
            }
        }

        String posStr = parts[parts.length - 1];

        StringBuilder topIdBuilder = new StringBuilder();
        for (int i = topIdStart; i < parts.length - 1; i++) {
            if (i > topIdStart) topIdBuilder.append("_");
            topIdBuilder.append(parts[i]);
        }
        String topId = topIdBuilder.toString();

        int position;
        try {
            position = Integer.parseInt(posStr);
        } catch (NumberFormatException e) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.position", "Invalid position!");
        }

        if (typeStr.equals("spaced")) {
            return handleSpacedPlaceholder(topId, position);
        }

        if (!typeStr.equals("name") && !typeStr.equals("value")) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.type", "Invalid type!");
        }

        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);
        if (top == null) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");
        }

        Optional<? extends TopEntry<?>> entryOpt = top.getEntry(position);
        if (entryOpt.isEmpty()) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.no-data", "-------");
        }

        TopEntry<?> entry = entryOpt.get();

        if (typeStr.equals("name")) {
            return entry.getDisplayName();
        }

        ValueFormatter formatter = inlineFormatter != null ? inlineFormatter : resolveTopFormatter(top);
        return formatter.format(entry.getValue());
    }

    private String handleSpacedPlaceholder(@NotNull String topId, int position) {
        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);

        if (top == null) return "";

        Optional<? extends TopEntry<?>> entryOpt = top.getEntry(position);
        if (entryOpt.isEmpty()) return "";

        TopEntry<?> entry = entryOpt.get();

        String fillChar = configService.provide(ConfigType.CONFIG).getString("spaced.char", "-");
        int maxLength = configService.provide(ConfigType.CONFIG).getInt("spaced.length", 40);

        String displayName = entry.getDisplayName();
        String formattedValue = resolveTopFormatter(top).format(entry.getValue());

        String cleanName = stripMinecraftColors(displayName);
        int spacesNeeded = maxLength - cleanName.length() - formattedValue.length();
        if (spacesNeeded < 1) spacesNeeded = 1;

        return fillChar.repeat(spacesNeeded) + " ";
    }

    private String handleDisplayNamePlaceholder(@NotNull String topId) {
        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);

        if (top == null) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");
        }

        String displayName = top.getConfig().getDisplayName();
        return (displayName == null || displayName.isEmpty()) ? topId : displayName;
    }

    private String handleDistanceAbovePlaceholder(@Nullable Player player, @NotNull String topId) {
        if (player == null) return notInTop();

        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);
        if (top == null) return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");

        @SuppressWarnings("unchecked") Top<UUID> uuidTop = (Top<UUID>) top;
        int myPos = uuidTop.getPosition(player.getUniqueId());
        if (myPos == -1) return notInTop();
        if (myPos == 1)  return configService.provide(ConfigType.LANG).getString("position.no-neighbor-above", "---");

        Optional<? extends TopEntry<?>> above = top.getEntry(myPos - 1);
        Optional<? extends TopEntry<?>> mine  = top.getEntry(myPos);
        if (above.isEmpty() || mine.isEmpty()) return configService.provide(ConfigType.LANG).getString("position.no-neighbor-above", "---");

        double distance = above.get().getValue() - mine.get().getValue();
        return resolveTopFormatter(top).format(distance);
    }

    private String handleDistanceBelowPlaceholder(@Nullable Player player, @NotNull String topId) {
        if (player == null) return notInTop();

        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);
        if (top == null) return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");

        @SuppressWarnings("unchecked") Top<UUID> uuidTop = (Top<UUID>) top;
        int myPos = uuidTop.getPosition(player.getUniqueId());
        if (myPos == -1) return notInTop();

        Optional<? extends TopEntry<?>> below = top.getEntry(myPos + 1);
        Optional<? extends TopEntry<?>> mine  = top.getEntry(myPos);
        if (below.isEmpty() || mine.isEmpty()) return configService.provide(ConfigType.LANG).getString("position.no-neighbor-below", "---");

        double distance = mine.get().getValue() - below.get().getValue();
        return resolveTopFormatter(top).format(distance);
    }

    private String handleAboveNamePlaceholder(@Nullable Player player, @NotNull String topId) {
        if (player == null) return notInTop();

        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);
        if (top == null) return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");

        @SuppressWarnings("unchecked") Top<UUID> uuidTop = (Top<UUID>) top;
        int myPos = uuidTop.getPosition(player.getUniqueId());
        if (myPos == -1) return notInTop();
        if (myPos == 1)  return configService.provide(ConfigType.LANG).getString("position.no-neighbor-above", "---");

        return top.getEntry(myPos - 1)
                .map(TopEntry::getDisplayName)
                .orElseGet(() -> configService.provide(ConfigType.LANG).getString("position.no-neighbor-above", "---"));
    }

    private String handleBelowNamePlaceholder(@Nullable Player player, @NotNull String topId) {
        if (player == null) return notInTop();

        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);
        if (top == null) return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");

        @SuppressWarnings("unchecked") Top<UUID> uuidTop = (Top<UUID>) top;
        int myPos = uuidTop.getPosition(player.getUniqueId());
        if (myPos == -1) return notInTop();

        return top.getEntry(myPos + 1)
                .map(TopEntry::getDisplayName)
                .orElseGet(() -> configService.provide(ConfigType.LANG).getString("position.no-neighbor-below", "---"));
    }

    private String handleMyPositionPlaceholder(@NotNull Player player, @NotNull String topId) {
        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);

        if (top == null) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");
        }

        @SuppressWarnings("unchecked")
        Top<UUID> uuidTop = (Top<UUID>) top;
        int position = uuidTop.getPosition(player.getUniqueId());

        return position == -1
                ? configService.provide(ConfigType.LANG).getString("position.not-in-top", "You are not in the top!")
                : String.valueOf(position);
    }

    private String notInTop() {
        return configService.provide(ConfigType.LANG).getString("position.not-in-top", "N/A");
    }

    private String stripMinecraftColors(@NotNull String text) {
        String result = text.replaceAll("[&§][0-9a-fk-or]", "");
        result = result.replaceAll("[&§]x([&§][0-9a-f]){6}", "");
        result = result.replaceAll("<[^>]+>", "");
        return result;
    }

    private ValueFormatter resolveTopFormatter(@NotNull Top<?> top) {
        return topFormatterCache.computeIfAbsent(top.getId(), k -> {
            String format = top.getConfig().getValueFormat();
            if (format == null) return DEFAULT_FORMATTER;
            ValueFormatter f = modeFormatters.get(format.toUpperCase());
            return f != null ? f : DEFAULT_FORMATTER;
        });
    }

    private Map<String, ValueFormatter> buildModeFormatters() {
        Map<String, ValueFormatter> map = new HashMap<>();
        map.put("EXACT",           new NumberValueFormatter(NumberFormatter.FormatMode.EXACT));
        map.put("ROUNDED",         new NumberValueFormatter(NumberFormatter.FormatMode.ROUNDED));
        map.put("COMPACT",         new NumberValueFormatter(NumberFormatter.FormatMode.COMPACT));
        map.put("COMPACT_ROUNDED", new NumberValueFormatter(NumberFormatter.FormatMode.COMPACT_ROUNDED));
        map.put("TIME",            new TimeValueFormatter(configService.provide(ConfigType.CONFIG)));
        return Collections.unmodifiableMap(map);
    }

    public void reload() {
        modeFormatters = buildModeFormatters();
        topFormatterCache.clear();
    }
}
