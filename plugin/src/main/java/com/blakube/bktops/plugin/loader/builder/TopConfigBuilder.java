package com.blakube.bktops.plugin.loader.builder;

import com.blakube.bktops.api.builder.Builder;
import com.blakube.bktops.api.storage.config.ConditionSet;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.plugin.condition.ConditionEvaluator;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TopConfigBuilder implements Builder<TopConfig, ConfigurationSection> {

    @Override
    public TopConfig build(@NotNull ConfigurationSection section) {
        return TopConfig.builder()
            .size(section.getInt("size", 10))
            .enableOnlineQueue(section.getBoolean("queues.online", true))
            .onlineQueueInterval(section.getInt("queues.online-interval", 100))
            .enableRotativeQueue(section.getBoolean("queues.rotative", false))
            .rotativeQueueSize(section.getInt("queues.rotative-size", 100))
            .batchSize(section.getInt("processing.batch-size", 10))
            .tickDelay(section.getInt("processing.tick-delay", 4))
            .displayName(section.getString("display-name", null))
            .conditionSet(parseConditionSet(section))
            .build();
    }

    private ConditionSet parseConditionSet(@NotNull ConfigurationSection section) {
        ConfigurationSection cond = section.getConfigurationSection("conditions");
        if (cond == null) return ConditionSet.EMPTY;

        List<String> expressions = cond.getStringList("expressions");
        int inactivityDays = cond.getInt("inactivity-days", 0);

        if (expressions.isEmpty() && inactivityDays <= 0) return ConditionSet.EMPTY;

        if (!expressions.isEmpty()) {
            ConditionEvaluator.precompile(expressions);
        }

        return new ConditionSet(expressions, inactivityDays);
    }
}
