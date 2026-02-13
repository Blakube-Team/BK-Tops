package com.blakube.bktops.plugin.hook.placeholder;

import com.blakube.bktops.api.config.ConfigType;
import com.blakube.bktops.plugin.service.config.ConfigService;
import com.blakube.bktops.plugin.service.time.ObtainTimeService;
import com.blakube.bktops.plugin.service.time.TimeFormatService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.top.TopEntry;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final ConfigService configService;
    private final TimeFormatService timeFormatService;
    private final ObtainTimeService obtainTimeService = new ObtainTimeService();

    private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        return new DecimalFormat("0.############", symbols);
    });

    public PlaceholderAPIHook(ConfigService configService) {
        this.configService = configService;
        this.timeFormatService = new TimeFormatService(configService);
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

        String[] parts = identifier.split("_");

        if (parts.length < 3) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.format", "Invalid format!");
        }

        String typeStr = parts[0];
        String posStr = parts[parts.length - 1];

        StringBuilder topIdBuilder = new StringBuilder();
        for (int i = 1; i < parts.length - 1; i++) {
            if (i > 1) {
                topIdBuilder.append("_");
            }
            topIdBuilder.append(parts[i]);
        }
        String topId = topIdBuilder.toString();

        if (!typeStr.equals("name") && !typeStr.equals("value")) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.type", "Invalid type!");
        }

        int position;
        try {
            position = Integer.parseInt(posStr);
        } catch (NumberFormatException e) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.position", "Invalid position!");
        }

        TopAPI api = TopAPIProvider.getInstance();
        Top<?> top = api.getTop(topId);
        if (top == null) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.top-id", "Unknown topId!");
        }

        Optional<? extends TopEntry<?>> entryOpt = top.getEntry(position);
        if (entryOpt.isEmpty()) {
            return configService.provide(ConfigType.LANG).getString("invalid-placeholder.no-data", "------");
        }

        TopEntry<?> entry = entryOpt.get();

        if (typeStr.equals("name")) {
            return entry.getDisplayName();
        } else {
            return FORMATTER.get().format(entry.getValue());
        }
    }
}