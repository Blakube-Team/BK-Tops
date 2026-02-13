package com.blakube.bktops.plugin.loader.builder;

import com.blakube.bktops.api.builder.Builder;
import com.blakube.bktops.api.storage.config.TopConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public final class TopConfigBuilder implements Builder<TopConfig, ConfigurationSection> {

    @Override
    public TopConfig build(@NotNull ConfigurationSection section) {
        return TopConfig.builder()
            .size(section.getInt("size", 10))
            .enableOnlineQueue(section.getBoolean("queues.online", true))
            .onlineQueueInterval(section.getInt("queues.online-interval", 100))
            .enableRotativeQueue(section.getBoolean("queues.rotative", false))
            .rotativeQueueSize(section.getInt("queues.rotative-size", 50))
            .batchSize(section.getInt("processing.batch-size", 5))
            .tickDelay(section.getInt("processing.tick-delay", 1))
            .build();
    }
}