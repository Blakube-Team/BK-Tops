package com.blakube.bktops.plugin.registry.history;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryRegistryTest {

    private HistoryRegistry newRegistry(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        return new HistoryRegistry(plugin);
    }

    private void writeFile(File dir, String name, int... positions) {
        dir.mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("top-id", "x");
        yaml.set("reset-date", "2026-09-05 14:30:00");
        var rows = new java.util.ArrayList<Map<String, Object>>();
        for (int p : positions) {
            var row = new java.util.LinkedHashMap<String, Object>();
            row.put("position", p);
            row.put("name", "Player" + p);
            row.put("value", String.valueOf(p * 10));
            rows.add(row);
        }
        yaml.set("entries", rows);
        try {
            yaml.save(new File(dir, name));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void listsPerTypeAndReadsBack(@TempDir File tmp) {
        HistoryRegistry registry = newRegistry(tmp);
        File resetDir = new File(new File(tmp, "history"), "reset");
        File exportDir = new File(new File(tmp, "history"), "exports");

        writeFile(resetDir, "kills_2026-09-05_14-30-00.yml", 1, 2, 3);
        writeFile(resetDir, "kills_2026-09-04_10-00-00.yml", 1);
        writeFile(resetDir, "deaths_2026-09-05_12-00-00.yml", 1, 2);
        writeFile(exportDir, "money_2026-09-05_09-00-00.yml", 1, 2, 3, 4);
        writeFile(resetDir, "not-a-registry.yml", 1);

        // per-type top listing, malformed file ignored
        assertEquals(List.of("deaths", "kills"), registry.listTopIds(HistoryRegistry.TYPE_RESET));
        assertEquals(List.of("money"), registry.listTopIds(HistoryRegistry.TYPE_EXPORTS));

        // dates newest first, scoped to type
        assertEquals(
                List.of("2026-09-05_14-30-00", "2026-09-04_10-00-00"),
                registry.listDates(HistoryRegistry.TYPE_RESET, "kills"));
        assertTrue(registry.listDates(HistoryRegistry.TYPE_EXPORTS, "kills").isEmpty());

        // round-trip read from the right type
        YamlConfiguration read = registry.read(HistoryRegistry.TYPE_EXPORTS, "money", "2026-09-05_09-00-00");
        assertNotNull(read);
        assertEquals(4, read.getMapList("entries").size());

        // wrong type / missing returns null; bad type is rejected
        assertNull(registry.read(HistoryRegistry.TYPE_RESET, "money", "2026-09-05_09-00-00"));
        assertNull(registry.read("bogus", "money", "2026-09-05_09-00-00"));
        assertFalse(HistoryRegistry.isValidType("bogus"));
        assertTrue(HistoryRegistry.isValidType(HistoryRegistry.TYPE_RESET));
    }
}
