package com.blakube.bktops.plugin.service.team;

import com.blakube.bktops.api.config.ConfigContainer;
import org.bukkit.Bukkit;

public class TeamHookHelpService {

    private final ConfigContainer configContainer;

    public TeamHookHelpService(ConfigContainer configContainer) {
        this.configContainer = configContainer;
    }

    public boolean isEnabled(String hook) {
        String path = hook + ".enabled";
        return configContainer.getBoolean(path, false);
    }

    public int getPriority(String hook) {
        String path = hook + ".priority";
        return configContainer.getInt(path, 0);
    }

    public boolean isAnyPluginEnabled(String... pluginNames) {
        if (pluginNames == null) return false;
        for (String name : pluginNames) {
            try {
                if (name != null && Bukkit.getPluginManager().isPluginEnabled(name)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public boolean resolveAvailability(String hookKey, String... pluginAliases) {
        try {
            return isEnabled(hookKey) && isAnyPluginEnabled(pluginAliases);
        } catch (Throwable t) {
            return false;
        }
    }

}