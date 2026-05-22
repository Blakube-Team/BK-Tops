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
            .enableOnlineQueue(section.getBoolean("queues.online", false))
            .onlineQueueInterval(section.getInt("queues.online-interval", 288000))
            .enableRotativeQueue(section.getBoolean("queues.rotative", false))
            .rotativeQueueSize(section.getInt("queues.rotative-size", 20))
            .rotativeQueueInterval(section.getInt("queues.rotative-interval", 40))
            .batchSize(section.getInt("processing.batch-size", 5))
            .tickDelay(section.getInt("processing.tick-delay", 20))
            .allowZeroValues(section.getBoolean("processing.allow-zero", false))
            .displayName(section.getString("display-name", null))
            .conditionSet(parseConditionSet(section))
            .valueFormat(section.getString("value-format", null))
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
