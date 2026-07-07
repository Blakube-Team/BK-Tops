package com.blakube.bktops.plugin.formatter;

import com.blakube.bktops.api.config.ConfigContainer;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.plugin.provider.DetectableValueKind;
import com.blakube.bktops.plugin.provider.ValueKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.blakube.bktops.plugin.debug.Debug;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;






public final class TopValueFormatter {

    private final ConfigContainer config;
    private final ValueFormatter defaultFormatter = new NumberValueFormatter(null);

    private volatile Map<String, ValueFormatter> modes;
    private final Map<String, ValueFormatter> explicitCache = new ConcurrentHashMap<>();

    
    
    
    private final Set<String> autoTimeLocked = ConcurrentHashMap.newKeySet();

    public TopValueFormatter(@NotNull ConfigContainer config) {
        this.config = config;
        this.modes = buildModes();
    }

    public void reload() {
        this.modes = buildModes();
        explicitCache.clear();
        autoTimeLocked.clear();
    }

    
    @Nullable
    public ValueFormatter forMode(@Nullable String mode) {
        return mode == null ? null : modes.get(mode.toUpperCase());
    }

    
    @NotNull
    public ValueFormatter defaultFormatter() {
        return defaultFormatter;
    }

    




    @NotNull
    public ValueFormatter resolve(@NotNull Top<?> top) {
        String format = top.getConfig().getValueFormat();

        if (format == null || format.equalsIgnoreCase("AUTO")) {
            return resolveAuto(top);
        }

        return explicitCache.computeIfAbsent(top.getId(), k -> {
            ValueFormatter f = modes.get(format.toUpperCase());
            return f != null ? f : defaultFormatter;
        });
    }

    private ValueFormatter resolveAuto(@NotNull Top<?> top) {
        if (autoTimeLocked.contains(top.getId())) {
            return modes.get("TIME");
        }
        if (top.getValueProvider() instanceof DetectableValueKind detectable
                && detectable.getDetectedValueKind() == ValueKind.TIME) {
            autoTimeLocked.add(top.getId());
            Debug.log("[{}] Auto-detected TIME values; locking time formatter", top.getId());
            return modes.get("TIME");
        }
        return defaultFormatter;
    }

    private Map<String, ValueFormatter> buildModes() {
        Map<String, ValueFormatter> map = new HashMap<>();
        map.put("EXACT",           new NumberValueFormatter(NumberFormatter.FormatMode.EXACT));
        map.put("ROUNDED",         new NumberValueFormatter(NumberFormatter.FormatMode.ROUNDED));
        map.put("COMPACT",         new NumberValueFormatter(NumberFormatter.FormatMode.COMPACT));
        map.put("COMPACT_ROUNDED", new NumberValueFormatter(NumberFormatter.FormatMode.COMPACT_ROUNDED));
        map.put("TIME",            new TimeValueFormatter(config));
        return Map.copyOf(map);
    }
}
