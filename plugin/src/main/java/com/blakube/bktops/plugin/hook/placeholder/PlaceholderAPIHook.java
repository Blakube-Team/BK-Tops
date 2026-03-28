package com.blakube.bktops.plugin.hook.placeholder;

import com.blakube.bktops.api.config.ConfigType;
import com.blakube.bktops.plugin.service.config.ConfigService;
import com.blakube.bktops.plugin.formatter.NumberFormatter;
import com.blakube.bktops.plugin.service.time.ObtainTimeService;
import com.blakube.bktops.plugin.service.time.TimeFormatService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.top.TopEntry;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final ConfigService configService;
    private final TimeFormatService timeFormatService;
    private final ObtainTimeService obtainTimeService = new ObtainTimeService();
    private final NumberFormatter numberFormatter;

    public PlaceholderAPIHook(ConfigService configService) {
        this.configService = configService;
        this.timeFormatService = new TimeFormatService(configService);
        this.numberFormatter = new NumberFormatter(configService.provide(ConfigType.CONFIG));
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
            String topId = identifier.substring("myposition_".length());
            return handleMyPositionPlaceholder(player, topId);
        }


        String[] parts = identifier.split("_");

        if (parts.length < 3) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.format", "Invalid format!");
        }

        String typeStr = parts[0];

        NumberFormatter.FormatMode formatOverride = null;
        if (typeStr.contains(":")) {
            String[] typeParts = typeStr.split(":");
            typeStr = typeParts[0];

            if (typeParts.length > 1) {
                try {
                    formatOverride = NumberFormatter.FormatMode.valueOf(typeParts[1].toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        String posStr = parts[parts.length - 1];

        StringBuilder topIdBuilder = new StringBuilder();
        for (int i = 1; i < parts.length - 1; i++) {
            if (i > 1) {
                topIdBuilder.append("_");
            }
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
        } else {
            return numberFormatter.format(entry.getValue(), formatOverride);
        }
    }

    private String handleSpacedPlaceholder(@NotNull String topId, int position) {
        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);

        if (top == null) {
            return "";
        }

        Optional<? extends TopEntry<?>> entryOpt = top.getEntry(position);
        if (entryOpt.isEmpty()) {
            return "";
        }

        TopEntry<?> entry = entryOpt.get();

        String fillChar = configService.provide(ConfigType.CONFIG).getString("spaced.char", "-");
        int maxLength = configService.provide(ConfigType.CONFIG).getInt("spaced.length", 40);

        String displayName = entry.getDisplayName();
        String formattedValue = numberFormatter.format(entry.getValue());

        String cleanName = stripMinecraftColors(displayName);

        int nameLength = cleanName.length();
        int valueLength = formattedValue.length();
        int totalContentLength = nameLength + valueLength;

        int spacesNeeded = maxLength - totalContentLength;

        if (spacesNeeded < 1) {
            spacesNeeded = 1;
        }

        return fillChar.repeat(spacesNeeded) + " ";
    }

    private String stripMinecraftColors(@NotNull String text) {
        String result = text.replaceAll("[&§][0-9a-fk-or]", "");
        result = result.replaceAll("[&§]x([&§][0-9a-f]){6}", "");
        result = result.replaceAll("<[^>]+>", "");

        return result;
    }

    private String handleMyPositionPlaceholder(@NotNull Player player, @NotNull String topId) {
        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);

        if (top == null) {
            return configService.provide(ConfigType.LANG)
                    .getString("invalid-placeholder.top-id", "Unknown topId!");
        }

        @SuppressWarnings("unchecked")
        Top<UUID> uuidTop = (Top<UUID>) top;
        int position = uuidTop.getPosition(player.getUniqueId());

        if (position == -1) {
            return configService.provide(ConfigType.LANG)
                    .getString("position.not-in-top", "You are not in the top!");
        }

        return String.valueOf(position);
    }

    public void reload() {
        numberFormatter.reload();
    }
}
