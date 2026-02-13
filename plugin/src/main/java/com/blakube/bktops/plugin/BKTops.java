package com.blakube.bktops.plugin;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.config.ConfigType;
import com.blakube.bktops.api.factory.TopFactory;
import com.blakube.bktops.api.config.ConfigContainer;
import com.blakube.bktops.api.registry.TopRegistry;
import com.blakube.bktops.plugin.command.BKTopsCommand;
import com.blakube.bktops.plugin.command.exception.ExceptionHandler;
import com.blakube.bktops.plugin.hook.metrics.Metrics;
import com.blakube.bktops.plugin.hook.placeholder.PlaceholderAPIHook;
import com.blakube.bktops.plugin.listener.PlayerJoinListener;
import com.blakube.bktops.plugin.listener.PlayerQuitListener;
import com.blakube.bktops.plugin.loader.TopLoader;
import com.blakube.bktops.plugin.message.MessageParser;
import com.blakube.bktops.plugin.message.MessageRepository;
import com.blakube.bktops.plugin.serializer.UUIDSerializer;
import com.blakube.bktops.plugin.service.config.ConfigService;
import com.blakube.bktops.plugin.service.notify.NotifyService;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseConnection;
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
    private NotifyService  notifyService;
    private TopAPI api;
    private TeamHandler teamManager;
    private com.blakube.bktops.plugin.service.team.TeamScoreService teamScoreService;

    @Override
    public void onEnable() {
        this.registry = new DefaultTopRegistry<>();
        this.teamManager = new TeamHandler();

        setUpConfig();
        setUpStorage();
        initTops();
        initServices();
        initApi();
        registerListeners();
        registerCommands();
        initHooks();
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

        DatabaseConnection.close();

        getLogger().info("BK-Tops disabled.");
    }

    private void setUpConfig() {
        configService = new ConfigService(this);
    }

    private void setUpStorage() {
        DatabaseConnection.init(this, configService.provide(ConfigType.DATABASE));
    }

    private void initApi() {
        this.api = new TopAPIImpl(registry);
        TopAPIProvider.setInstance(api);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(registry), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(registry), this);
    }

    private void initHooks() {

        int pluginId = 29441;
        Metrics metrics = new Metrics(this, pluginId);

        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new PlaceholderAPIHook(configService).register();
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

        try {
            this.teamScoreService = new com.blakube.bktops.plugin.service.team.TeamScoreService(this, this.teamManager);
        } catch (Throwable t) {
            getLogger().warning("Failed to initialize TeamScoreService: " + t.getMessage());
        }
    }

    private void registerCommands() {

        final var exceptionHandler = new ExceptionHandler(notifyService);

        var lamp = BukkitLamp.builder(this)
                .exceptionHandler(exceptionHandler)
                .build();

        lamp.register(
                new BKTopsCommand(notifyService, this));
    }

    private void initTops() {
        registry.clear();
        TopFactory<UUID> factory = new DefaultTopFactory<>(new UUIDSerializer());

        TopLoader loader = new TopLoader(this, configService.provide(ConfigType.TOPS), factory, registry);
        loader.load();
    }

    public void reloadPlugin() {
        if (scheduler != null) {
            scheduler.stop();
        }

        configService.reloadAll();
        
        DatabaseConnection.close();
        setUpStorage();

        initTops();
        initScheduler();

        getLogger().info("BK-Tops reloaded. Tops loaded: " + registry.size());
    }

    public TeamHandler getTeamManager() {
        return teamManager;
    }

    public com.blakube.bktops.plugin.service.team.TeamScoreService getTeamScoreService() {
        return teamScoreService;
    }

}
