package com.blakube.bktops.plugin;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.config.ConfigType;
import com.blakube.bktops.api.factory.TopFactory;
import com.blakube.bktops.api.config.ConfigContainer;
import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.plugin.command.BKTopsCommand;
import com.blakube.bktops.plugin.command.exception.ExceptionHandler;
import com.blakube.bktops.plugin.formatter.NumberFormatter;
import com.blakube.bktops.plugin.formatter.NumberFormatterProvider;
import com.blakube.bktops.plugin.formatter.TopValueFormatter;
import com.blakube.bktops.plugin.formatter.TopValueFormatterProvider;
import com.blakube.bktops.plugin.hook.metrics.Metrics;
import com.blakube.bktops.plugin.hook.placeholder.PlaceholderAPIHook;
import com.blakube.bktops.plugin.listener.PlayerJoinListener;
import com.blakube.bktops.plugin.listener.PlayerQuitListener;
import com.blakube.bktops.plugin.loader.TopLoader;
import com.blakube.bktops.plugin.message.MessageParser;
import com.blakube.bktops.plugin.message.MessageRepository;
import com.blakube.bktops.plugin.notification.DiscordWebhookSender;
import com.blakube.bktops.plugin.notification.NotificationService;
import com.blakube.bktops.plugin.notification.TopNotificationListener;
import com.blakube.bktops.plugin.reward.PendingRewardService;
import com.blakube.bktops.plugin.reward.config.RewardConfigLoader;
import com.blakube.bktops.plugin.reward.config.RewardConfigRegistry;
import com.blakube.bktops.plugin.reward.item.RTagItemSerializer;
import com.blakube.bktops.plugin.reward.listener.TopRewardListener;
import com.blakube.bktops.plugin.reward.storage.PendingRewardDAO;
import com.blakube.bktops.plugin.serializer.UUIDSerializer;
import com.blakube.bktops.plugin.service.config.ConfigService;
import com.blakube.bktops.plugin.service.notify.NotifyService;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseConnection;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import com.blakube.bktops.plugin.top.factory.DefaultTopFactory;
import com.blakube.bktops.plugin.registry.DefaultTopRegistry;
import com.blakube.bktops.plugin.schedule.ProcessorScheduler;
import com.blakube.bktops.plugin.hook.team.TeamHandler;
import com.blakube.bktops.plugin.service.team.TeamHookHelpService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.bukkit.BukkitLamp;

import java.util.UUID;

public class BKTops extends JavaPlugin {

    private ConfigService  configService;
    private TopRegistry<UUID> registry;
    private ProcessorScheduler scheduler;
    private MessageParser messageManager;
    private MessageRepository messageRepository;
    private PlaceholderAPIHook placeholderAPIHook;
    private NotifyService  notifyService;
    private DiscordWebhookSender discordWebhookSender;
    private NotificationService notificationService;
    private TopAPI api;
    private TeamHandler teamManager;
    private RewardConfigRegistry rewardConfigRegistry;
    private PendingRewardService pendingRewardService;
    private RTagItemSerializer rewardItemSerializer;

    @Override
    public void onEnable() {
        this.registry = new DefaultTopRegistry<>();
        this.teamManager = new TeamHandler();

        setUpConfig();
        initDebug();
        setUpStorage();
        initNumberFormatter();
        initHooks();
        initServices();
        initNotifications();
        initApi();
        initRewards();
        registerListeners();
        initTops();
        registerCommands();
        initScheduler();

        getLogger().info("BK-Tops enabled. Tops loaded: " + registry.size());
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
        }

        if (TopAPIProvider.isAvailable()) {
            TopAPIProvider.unload();
        }

        if (registry != null) {
            registry.clear();
        }

        DatabaseExecutors.awaitPendingTasks();
        DatabaseConnection.close();

        NumberFormatterProvider.unload();
        TopValueFormatterProvider.unload();

        getLogger().info("BK-Tops disabled.");
    }

    private void setUpConfig() {
        configService = new ConfigService(this);
    }

    private void initDebug() {
        boolean debugEnabled = configService.provide(ConfigType.CONFIG).getBoolean("debug", false);
        com.blakube.bktops.plugin.debug.Debug.init(getLogger(), debugEnabled);
        if (debugEnabled) {
            getLogger().info("Debug logging is ENABLED (config.yml -> debug: true).");
        }
    }

    private void setUpStorage() {
        DatabaseConnection.init(this, configService.provide(ConfigType.DATABASE));
    }

    private void initNumberFormatter() {
        NumberFormatter formatter = new NumberFormatter(configService.provide(ConfigType.CONFIG));
        NumberFormatterProvider.setInstance(formatter);
        TopValueFormatterProvider.setInstance(new TopValueFormatter(configService.provide(ConfigType.CONFIG)));
        getLogger().info("NumberFormatter initialized with mode: " + configService.provide(ConfigType.CONFIG).getString("number-format.mode", "EXACT"));
    }

    private void initApi() {
        this.api = new TopAPIImpl(registry);
        TopAPIProvider.setInstance(api);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(registry, pendingRewardService), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(registry), this);
        Bukkit.getPluginManager().registerEvents(new TopNotificationListener(notificationService), this);
        Bukkit.getPluginManager().registerEvents(new TopRewardListener(rewardConfigRegistry, pendingRewardService, teamManager), this);
    }

    private void initNotifications() {
        this.discordWebhookSender = new DiscordWebhookSender(this, configService.provide(ConfigType.DISCORD));
        this.notificationService  = new NotificationService(configService.provide(ConfigType.NOTIFICATIONS), discordWebhookSender);
    }

    private void initRewards() {
        if (rewardConfigRegistry == null) {
            rewardConfigRegistry = new RewardConfigRegistry();
        }

        new RewardConfigLoader(configService.provide(ConfigType.TOPS), getLogger()).loadInto(rewardConfigRegistry);

        if (rewardItemSerializer == null) {
            rewardItemSerializer = new RTagItemSerializer();
        }

        if (pendingRewardService == null) {
            pendingRewardService = new PendingRewardService(
                    this,
                    new PendingRewardDAO(),
                    rewardItemSerializer,
                    registry,
                    notificationService
            );
        }

        pendingRewardService.loadPendingFromStorage();
    }

    private void initHooks() {

        int pluginId = 29441;
        Metrics metrics = new Metrics(this, pluginId);

        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                placeholderAPIHook = new PlaceholderAPIHook(configService);
                placeholderAPIHook.register();
                getLogger().info("PlaceholderAPI detected: BK-Tops placeholders registered.");
            }
        } catch (Throwable t) {
            getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
        }

        try {
            ConfigContainer hooksCfg = configService.provide(ConfigType.HOOKS);
            TeamHookHelpService helper = new TeamHookHelpService(hooksCfg);

            teamManager.initAllHooks(helper);

            int count = teamManager.getRegisteredHooks().size();
            getLogger().info("TeamManager initialized. Registered team hooks: " + count);

            teamManager.getPrimaryHook().ifPresentOrElse(primary ->
                            getLogger().info("Primary team hook selected: " + primary.getPluginName() + " (priority=" + primary.getPriority() + ")"),
                    () -> getLogger().info("No primary team hook available (none enabled or present)."));
        } catch (Throwable t) {
            getLogger().warning("Error while loading team hooks from hooks.yml: " + t.getMessage());
        }
    }

    private void initScheduler() {
        this.scheduler = new ProcessorScheduler(this, registry);
        this.scheduler.start();
    }

    private void initServices() {
        messageManager = new MessageParser();
        messageRepository = new MessageRepository(configService);
        notifyService = new NotifyService(messageManager, messageRepository);
    }

    private void registerCommands() {

        final var exceptionHandler = new ExceptionHandler(notifyService);

        var lamp = BukkitLamp.builder(this)
                .exceptionHandler(exceptionHandler)
                .build();

        lamp.register(
                new BKTopsCommand(notifyService, notificationService, this, rewardItemSerializer));
    }

    private void initTops() {
        registry.clear();
        TopFactory<UUID> factory = new DefaultTopFactory<>(new UUIDSerializer(), this);

        TopLoader loader = new TopLoader(this, configService.provide(ConfigType.TOPS), factory, registry);
        loader.load();
    }

    public void reloadPlugin() {
        if (scheduler != null) {
            scheduler.stop();
        }

        configService.reloadAll();
        initDebug();

        if (NumberFormatterProvider.isAvailable()) {
            NumberFormatterProvider.getInstance().reload();
        }

        if (TopValueFormatterProvider.isAvailable()) {
            TopValueFormatterProvider.getInstance().reload();
        }

        if (placeholderAPIHook != null) {
            placeholderAPIHook.reload();
        }

        if (notificationService != null) {
            notificationService.reload(configService.provide(ConfigType.NOTIFICATIONS));
        }
        if (discordWebhookSender != null) {
            discordWebhookSender.reload(configService.provide(ConfigType.DISCORD));
        }

        DatabaseExecutors.awaitPendingTasks();
        DatabaseConnection.close();
        setUpStorage();

        initRewards();
        reloadTeamHooks();
        initTops();
        initScheduler();

        getLogger().info("BK-Tops reloaded. Tops loaded: " + registry.size());
    }

    private void reloadTeamHooks() {
        try {
            new java.util.ArrayList<>(teamManager.getRegisteredHooks())
                    .forEach(teamManager::unregisterHook);

            ConfigContainer hooksCfg = configService.provide(ConfigType.HOOKS);
            com.blakube.bktops.plugin.service.team.TeamHookHelpService helper =
                    new com.blakube.bktops.plugin.service.team.TeamHookHelpService(hooksCfg);
            teamManager.initAllHooks(helper);

            getLogger().info("Team hooks reloaded. Registered: " + teamManager.getRegisteredHooks().size());
        } catch (Throwable t) {
            getLogger().warning("Error while reloading team hooks: " + t.getMessage());
        }
    }

    public TeamHandler getTeamManager() {
        return teamManager;
    }

    public ConfigService getConfigService() {
        return configService;
    }

}
