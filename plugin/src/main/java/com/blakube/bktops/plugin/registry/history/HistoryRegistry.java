package com.blakube.bktops.plugin.registry.history;

import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.formatter.TopValueFormatterProvider;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HistoryRegistry {

    public static final String TYPE_RESET = "reset";
    public static final String TYPE_EXPORTS = "exports";

    private static volatile HistoryRegistry instance;

    public static final DateTimeFormatter FILE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter READABLE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Pattern FILE_PATTERN =
            Pattern.compile("^(.*)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.yml$");

    private final JavaPlugin plugin;
    private final File resetDir;
    private final File exportDir;

    public HistoryRegistry(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        File base = new File(plugin.getDataFolder(), "history");
        this.resetDir = new File(base, TYPE_RESET);
        this.exportDir = new File(base, TYPE_EXPORTS);
        instance = this;
    }

    @Nullable
    public static HistoryRegistry getInstance() {
        return instance;
    }

    public void writeReset(@NotNull String topId, long resetAtMillis, @NotNull String schedule,
                           @NotNull List<? extends TopEntry<?>> entries) {
        write(resetDir, topId, resetAtMillis, schedule, entries);
    }

    public void writeExport(@NotNull String topId, @NotNull List<? extends TopEntry<?>> entries) {
        write(exportDir, topId, System.currentTimeMillis(), "EXPORT", entries);
    }

    private void write(@NotNull File dir, @NotNull String topId, long atMillis, @NotNull String schedule,
                       @NotNull List<? extends TopEntry<?>> entries) {
        List<Map<String, Object>> rows = new ArrayList<>(entries.size());
        Top<?> top = TopAPIProvider.isAvailable() ? TopAPIProvider.getInstance().getTop(topId) : null;

        for (TopEntry<?> entry : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("position", entry.getPosition());
            row.put("name", entry.getDisplayName());
            row.put("value", formatValue(top, entry.getValue()));
            row.put("raw-value", entry.getValue());
            row.put("uuid", String.valueOf(entry.getIdentifier()));
            rows.add(row);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!dir.exists() && !dir.mkdirs()) {
                    plugin.getLogger().warning("[BK-Tops] Could not create history folder " + dir.getName() + ".");
                    return;
                }
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("top-id", topId);
                yaml.set("schedule", schedule);
                yaml.set("reset-at", atMillis);
                yaml.set("reset-date", READABLE_DATE.format(Instant.ofEpochMilli(atMillis)));
                yaml.set("entries", rows);

                String date = FILE_DATE.format(Instant.ofEpochMilli(atMillis));
                yaml.save(new File(dir, sanitize(topId) + "_" + date + ".yml"));
            } catch (Exception e) {
                plugin.getLogger().warning("[BK-Tops] Failed to write history for " + topId + ": " + e.getMessage());
            }
        });
    }

    @NotNull
    public List<String> listTopIds(@NotNull String type) {
        TreeSet<String> ids = new TreeSet<>();
        for (Matcher m : matchedFiles(type)) ids.add(m.group(1));
        return new ArrayList<>(ids);
    }

    @NotNull
    public List<String> listDates(@NotNull String type, @NotNull String topId) {
        TreeSet<String> dates = new TreeSet<>(java.util.Comparator.reverseOrder());
        for (Matcher m : matchedFiles(type)) {
            if (m.group(1).equals(topId)) dates.add(m.group(2));
        }
        return new ArrayList<>(dates);
    }

    @Nullable
    public YamlConfiguration read(@NotNull String type, @NotNull String topId, @NotNull String date) {
        File dir = dirFor(type);
        if (dir == null) return null;
        File file = new File(dir, sanitize(topId) + "_" + date + ".yml");
        if (!file.isFile()) return null;
        return YamlConfiguration.loadConfiguration(file);
    }

    public static boolean isValidType(@Nullable String type) {
        return TYPE_RESET.equals(type) || TYPE_EXPORTS.equals(type);
    }

    @Nullable
    private File dirFor(@NotNull String type) {
        if (TYPE_RESET.equals(type)) return resetDir;
        if (TYPE_EXPORTS.equals(type)) return exportDir;
        return null;
    }

    private List<Matcher> matchedFiles(@NotNull String type) {
        List<Matcher> out = new ArrayList<>();
        File dir = dirFor(type);
        if (dir == null) return out;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return out;
        for (File f : files) {
            Matcher m = FILE_PATTERN.matcher(f.getName());
            if (m.matches()) out.add(m);
        }
        return out;
    }

    private String formatValue(@Nullable Top<?> top, double value) {
        if (top != null && TopValueFormatterProvider.isAvailable()) {
            try {
                return TopValueFormatterProvider.getInstance().resolve(top).format(value);
            } catch (Exception ignored) {}
        }
        return String.valueOf(value);
    }

    private static String sanitize(String topId) {
        return topId.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
