package com.blakube.bktops.plugin;

import com.blakube.bktops.api.config.ConfigContainer;
import com.blakube.bktops.api.config.ConfigException;
import com.blakube.bktops.api.config.ConfigType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TestConfigContainer implements ConfigContainer {

    private final Map<String, Object> values;

    public TestConfigContainer(Map<String, Object> values) {
        this.values = values;
    }

    @Override
    public String getString(String path, String defaultValue) {
        Object v = values.get(path);
        return v instanceof String s ? s : defaultValue;
    }

    @Override
    public int getInt(String path, int defaultValue) {
        Object v = values.get(path);
        return v instanceof Number n ? n.intValue() : defaultValue;
    }

    @Override
    public boolean getBoolean(String path, boolean defaultValue) {
        Object v = values.get(path);
        return v instanceof Boolean b ? b : defaultValue;
    }

    @Override
    public double getDouble(String path, double defaultValue) {
        Object v = values.get(path);
        return v instanceof Number n ? n.doubleValue() : defaultValue;
    }

    @Override
    public long getLong(String path, long defaultValue) {
        Object v = values.get(path);
        return v instanceof Number n ? n.longValue() : defaultValue;
    }

    @Override public Optional<String> getString(String path) { return Optional.ofNullable(getString(path, null)); }
    @Override public Optional<Integer> getInt(String path) { return Optional.of(getInt(path, 0)); }
    @Override public Optional<Boolean> getBoolean(String path) { return Optional.of(getBoolean(path, false)); }
    @Override public Optional<Double> getDouble(String path) { return Optional.of(getDouble(path, 0.0)); }
    @Override public Optional<Long> getLong(String path) { return Optional.of(getLong(path, 0L)); }
    @Override public Optional<List<String>> getStringList(String path) { return Optional.empty(); }
    @Override public List<String> getStringList(String path, List<String> defaultValue) { return defaultValue; }
    @Override public ConfigurationSection getConfigurationSection(String path) { return null; }
    @Override public boolean hasPath(String path) { return values.containsKey(path); }
    @Override public Set<String> getKeys(String path) { return Set.of(); }
    @Override public String getName() { return "test"; }
    @Override public ConfigType getType() { return ConfigType.CONFIG; }
    @Override public boolean isLoaded() { return true; }
    @Override public void requirePath(String path) throws ConfigException {}
    @Override public <T> T getRequired(String path, Class<T> type) throws ConfigException { return null; }
}
