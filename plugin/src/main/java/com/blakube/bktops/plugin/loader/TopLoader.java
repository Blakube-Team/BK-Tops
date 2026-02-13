package com.blakube.bktops.plugin.loader;

import com.blakube.bktops.api.config.ConfigContainer;
import com.blakube.bktops.api.config.ConfigException;
import com.blakube.bktops.api.factory.TopFactory;
import com.blakube.bktops.api.loader.Loader;
import com.blakube.bktops.api.provider.ValueProvider;
import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.api.resolver.NameResolver;
import com.blakube.bktops.api.storage.TopStorage;
import com.blakube.bktops.api.storage.config.TopConfig;
import com.blakube.bktops.api.timed.ResetSchedule;
import com.blakube.bktops.api.timed.TimedTop;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.plugin.loader.builder.TopConfigBuilder;
import com.blakube.bktops.plugin.provider.PlaceholderValueProvider;
import com.blakube.bktops.plugin.provider.TeamValueProvider;
import com.blakube.bktops.plugin.provider.TimedValueProvider;
import com.blakube.bktops.plugin.resolver.PlayerNameResolver;
import com.blakube.bktops.plugin.resolver.TeamNameResolver;
import com.blakube.bktops.plugin.serializer.UUIDSerializer;
import com.blakube.bktops.plugin.storage.wrapper.TopStorageImpl;
import com.blakube.bktops.plugin.storage.database.dao.SnapshotDAO;
import com.blakube.bktops.plugin.storage.database.dao.TimedMetaDAO;
import com.blakube.bktops.plugin.BKTops;
import com.blakube.bktops.plugin.service.team.TeamScoreService;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class TopLoader implements Loader<TopRegistry<UUID>> {

    private final JavaPlugin plugin;
    private final ConfigContainer topsConfig;
    private final TopFactory<UUID> factory;
    private final TopRegistry<UUID> registry;
    private final Logger logger;

    public TopLoader(@NotNull JavaPlugin plugin,
                     @NotNull ConfigContainer topsConfig,
                     @NotNull TopFactory<UUID> factory,
                     @NotNull TopRegistry<UUID> registry) {
        this.plugin = plugin;
        this.topsConfig = topsConfig;
        this.factory = factory;
        this.registry = registry;
        this.logger = plugin.getLogger();
    }

    @Override
    public TopRegistry<UUID> load() {
        Set<String> topIds = topsConfig.getKeys("");

        if (topIds.isEmpty()) {
            logger.warning("There are not definitions at tops.yml!");
            return registry;
        }

        logger.info("Loading " + topIds.size() + " tops...");

        for (String topId : topIds) {
            try {
                loadTop(topId);
                logger.info("  Done: " + topId);
            } catch (Exception e) {
                logger.severe("  Exception loading top '" + topId + "': " + e.getMessage());
            }
        }

        logger.info("Loaded tops amount: " + registry.size());
        return registry;
    }

    private void loadTop(String topId) throws ConfigException {
        String basePath = topId + ".";

        topsConfig.requirePath(basePath + "type");

        String type = topsConfig.getString(basePath + "type", "normal");

        TopConfigBuilder configBuilder = new TopConfigBuilder();
        var topConfig = configBuilder.build(topsConfig.getConfigurationSection(topId));

        UUIDSerializer serializer = new UUIDSerializer();
        TopStorage<UUID> storage = new TopStorageImpl<>(topId, serializer);
        storage.initialize();

        String providerPlaceholder = topsConfig.getString(basePath + "provider", null);
        if (providerPlaceholder == null || providerPlaceholder.isBlank()) {
            throw new ConfigException("Missing placeholder for top '" + topId + "'");
        }

        String lowerProvider = providerPlaceholder.toLowerCase();
        if (lowerProvider.contains("%bktops_")) {
            throw new ConfigException(
                "Invalid provider for top '" + topId + "': '" + providerPlaceholder + "'. " +
                "Do not use BK-Tops placeholders as data providers (it causes recursion). " +
                "Use a base placeholder (e.g., Vault balance) instead."
            );
        }
        ValueProvider<UUID> baseProvider;
        NameResolver<UUID> nameResolver;

        boolean isTeam = type.equalsIgnoreCase("team") || type.equalsIgnoreCase("team-timed");
        if (isTeam) {
            BKTops bkTops = (plugin instanceof BKTops) ? (BKTops) plugin : null;
            if (bkTops == null) {
                throw new ConfigException("Team tops require BK-Tops plugin context");
            }
            TeamScoreService tss = new TeamScoreService(bkTops, bkTops.getTeamManager());
            baseProvider = new TeamValueProvider(plugin, tss, providerPlaceholder);
            nameResolver = new TeamNameResolver(bkTops.getTeamManager(), new PlayerNameResolver());
        } else {
            baseProvider = new PlaceholderValueProvider(plugin, providerPlaceholder);
            nameResolver = new PlayerNameResolver();
        }

        if (type.equalsIgnoreCase("timed") || type.equalsIgnoreCase("team-timed")) {
            createTimedTop(topId, topConfig, baseProvider, nameResolver, storage, serializer);
        } else {
            createNormalTop(topId, topConfig, baseProvider, nameResolver, storage);
        }
    }

    private void createNormalTop(String topId,
                                 TopConfig config,
                                 ValueProvider<UUID> provider,
                                 NameResolver<UUID> resolver,
                                 TopStorage<UUID> storage) {
        Top<UUID> top = factory.createTop(topId, config, provider, resolver, storage);
        registry.register(top);
    }

    private void createTimedTop(String topId,
                                TopConfig config,
                                ValueProvider<UUID> baseProvider,
                                NameResolver<UUID> resolver,
                                TopStorage<UUID> storage,
                                UUIDSerializer serializer) throws ConfigException {
        String basePath = topId + ".";

        topsConfig.requirePath(basePath + "reset");
        String resetRaw = topsConfig.getString(basePath + "reset", "weekly");

        String cron = isCronExpression(resetRaw) ? resetRaw : null;
        ResetSchedule schedule = createResetSchedule(resetRaw);

        SnapshotDAO<UUID> snapshotDAO = new SnapshotDAO<>(topId, serializer);

        TimedValueProvider<UUID> timedProvider = new TimedValueProvider<>(
                topId,
                baseProvider,
                snapshotDAO
        );

        TimedTop<UUID> timedTop = factory.createTimedTop(
                topId, config, timedProvider, resolver, storage, schedule
        );

        if (timedTop instanceof com.blakube.bktops.plugin.top.DefaultTimedTop) {
            var impl = (com.blakube.bktops.plugin.top.DefaultTimedTop<UUID>) timedTop;
            TimedMetaDAO metaDAO = new TimedMetaDAO(topId);
            impl.initTimingPersistence(metaDAO, cron);
        }

        registry.register(timedTop);
    }

    private ResetSchedule createResetSchedule(String resetType) {
        String lowered = resetType == null ? "weekly" : resetType.toLowerCase();
        if (isCronExpression(lowered)) {
            return ResetSchedule.weekly();
        }
        switch (lowered) {
            case "hourly":
                return ResetSchedule.hourly();
            case "daily":
                return ResetSchedule.daily();
            case "weekly":
                return ResetSchedule.weekly();
            case "monthly":
                return ResetSchedule.monthly();
            default:
                logger.warning("Unknown reset '" + resetType + "', using weekly");
                return ResetSchedule.weekly();
        }
    }

    private boolean isCronExpression(String value) {
        if (value == null) return false;
        String[] parts = value.trim().split("\\s+");
        return parts.length == 5 && value.chars().anyMatch(ch -> ch == '*' || ch == '/' || ch == '-' || ch == ' ');
    }
}