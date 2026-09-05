package com.blakube.bktops.plugin.command;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.config.ConfigType;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.condition.ConditionEvaluator;
import com.blakube.bktops.plugin.BKTops;
import com.blakube.bktops.plugin.formatter.TopValueFormatterProvider;
import com.blakube.bktops.plugin.notification.EventContext;
import com.blakube.bktops.plugin.notification.NotificationService;
import com.blakube.bktops.plugin.registry.history.HistoryRegistry;
import com.blakube.bktops.plugin.reward.item.RTagItemSerializer;
import com.blakube.bktops.plugin.service.notify.NotifyService;
import com.blakube.bktops.plugin.storage.config.ConfigContainerImpl;
import com.blakube.bktops.plugin.storage.config.Configuration;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.node.ExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Command("bktops")
@CommandPermission("bk-tops.admin")
public class BKTopsCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final NotifyService notifyService;
    private final NotificationService notificationService;
    private final BKTops plugin;
    private final RTagItemSerializer rewardItemSerializer;

    public BKTopsCommand(NotifyService notifyService,
                         NotificationService notificationService,
                         BKTops plugin,
                         RTagItemSerializer rewardItemSerializer) {
        this.notifyService = notifyService;
        this.notificationService = notificationService;
        this.plugin = plugin;
        this.rewardItemSerializer = rewardItemSerializer;
    }

    @Subcommand("reset <topId>")
    public void reset(BukkitCommandActor actor, @Named("topId") String topId) {
        TopAPI api = TopAPIProvider.getInstance();
        var top = api.getTop(topId);

        if (top == null) {
            notifyService.sendChat(actor.sender(), "message.top-not-found");
            return;
        }

        top.reset();
        notifyService.sendChat(actor.sender(), "message.top-reset");
    }

    @Subcommand("reload")
    public void reload(BukkitCommandActor actor) {
        plugin.reloadPlugin();
        notifyService.sendChat(actor.sender(), "message.config-reloaded");
    }

    @Subcommand("notify test update")
    public void notifyTestUpdate(BukkitCommandActor actor) {
        CommandSender sender = actor.sender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>This test must be run by a player."));
            return;
        }
        notificationService.notifyTopPositionUpdate(EventContext.positionUpdate(
                player.getName(), "1", "3", "Example_top", "Example_top", "5000", "3200"));
        sender.sendMessage(MM.deserialize("<yellow>Fired: top-position-update"));
    }

    @Subcommand("notify test reset")
    public void notifyTestReset(BukkitCommandActor actor) {
        notificationService.notifyTimedTopReset(EventContext.timedReset("Example_top", "Example_top"));
        actor.sender().sendMessage(MM.deserialize("<yellow>Fired: timed-top-reset"));
    }

    @Subcommand("rewards additem <topId> <position> <amount>")
    public void addRewardItem(BukkitCommandActor actor,
                              @Named("topId") String topId,
                              @Named("position") String position,
                              @Named("amount") int amount) {
        CommandSender sender = actor.sender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>This command must be run by a player."));
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(MM.deserialize("<red>Amount must be positive."));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            sender.sendMessage(MM.deserialize("<red>Hold the reward item in your main hand."));
            return;
        }

        Configuration config = getTopsConfiguration(sender);
        if (config == null || !config.contains(topId)) return;

        ItemStack rewardItem = hand.clone();
        rewardItem.setAmount(amount);

        String basePath = topId + ".rewards.positions." + position + ".items";
        ConfigurationSection items = config.getConfigurationSection(basePath);
        if (items == null) {
            items = config.createSection(basePath);
        }

        int nextId = nextNumericKey(items);
        config.set(topId + ".rewards.enabled", true);
        config.set(basePath + "." + nextId + ".item", rewardItemSerializer.serialize(rewardItem));
        config.set(basePath + "." + nextId + ".amount", amount);
        plugin.getConfigService().save(ConfigType.TOPS);

        sender.sendMessage(MM.deserialize("<green>Reward item added to <yellow>" + topId
                + "</yellow> position <yellow>" + position + "</yellow>. Reloading BK-Tops is required."));
    }

    @Subcommand("rewards addcommand <topId> <position> <command>")
    public void addRewardCommand(BukkitCommandActor actor,
                                 @Named("topId") String topId,
                                 @Named("position") String position,
                                 @Named("command") String command) {
        CommandSender sender = actor.sender();
        if (command == null || command.isBlank()) {
            sender.sendMessage(MM.deserialize("<red>Command cannot be empty."));
            return;
        }

        Configuration config = getTopsConfiguration(sender);
        if (config == null || !config.contains(topId)) return;

        String path = topId + ".rewards.positions." + position + ".commands";
        List<String> commands = new ArrayList<>(config.getStringList(path));
        commands.add(command.startsWith("/") ? command.substring(1) : command);

        config.set(topId + ".rewards.enabled", true);
        config.set(path, commands);
        plugin.getConfigService().save(ConfigType.TOPS);

        sender.sendMessage(MM.deserialize("<green>Reward command added to <yellow>" + topId
                + "</yellow> position <yellow>" + position + "</yellow>. Reloading BK-Tops is required."));
    }

    @Subcommand("rewards list <topId>")
    public void listRewards(BukkitCommandActor actor, @Named("topId") String topId) {
        CommandSender sender = actor.sender();
        Configuration config = getTopsConfiguration(sender);
        if (config == null || !config.contains(topId)) return;

        ConfigurationSection positions = config.getConfigurationSection(topId + ".rewards.positions");
        if (positions == null || positions.getKeys(false).isEmpty()) {
            sender.sendMessage(MM.deserialize("<yellow>No rewards configured for " + topId + "."));
            return;
        }

        sender.sendMessage(MM.deserialize("<gold><bold>BK-Tops Rewards</bold></gold> <gray>|</gray> <white>" + topId));
        for (String position : positions.getKeys(false)) {
            ConfigurationSection section = positions.getConfigurationSection(position);
            if (section == null) continue;
            int itemCount = section.getConfigurationSection("items") != null
                    ? section.getConfigurationSection("items").getKeys(false).size()
                    : 0;
            int commandCount = section.getStringList("commands").size();
            String mode = section.getString("team-reward-mode", "default");
            sender.sendMessage(MM.deserialize("<gray>  " + position + ": <yellow>" + itemCount
                    + "</yellow> item(s), <yellow>" + commandCount + "</yellow> command(s), mode <white>" + mode));
        }
    }

    @Subcommand("debug <player>")
    public void debug(BukkitCommandActor actor, @Named("player") OfflinePlayer target) {
        CommandSender sender = actor.sender();
        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : uuid.toString();

        sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));
        sender.sendMessage(MM.deserialize("<gold><bold>BK-Tops Debug</bold></gold> <gray>|</gray> <white>" + name));
        sender.sendMessage(MM.deserialize("<gray>UUID: " + uuid));
        sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));

        TopAPI api = TopAPIProvider.getInstance();
        Collection<Top> tops = api.getAllTops();

        if (tops.isEmpty()) {
            sender.sendMessage(MM.deserialize("<red>No tops are registered."));
            return;
        }

        for (Top top : tops) {
            String topId = top.getId();
            sender.sendMessage(MM.deserialize("<yellow>Top: " + topId));

            boolean bypass = target.isOnline() && target.getPlayer().hasPermission("bktops.bypass." + topId);
            sender.sendMessage(MM.deserialize("  <gray>Bypass permission: " + (bypass ? "<red>YES (Will be skipped)" : "<green>NO")));

            if (!top.getConfig().getConditionSet().isEmpty()) {
                boolean passes = ConditionEvaluator.passes(top.getConfig().getConditionSet(), uuid);
                sender.sendMessage(MM.deserialize("  <gray>Conditions: " + (passes ? "<green>PASS" : "<red>FAIL")));
            } else {
                sender.sendMessage(MM.deserialize("  <gray>Conditions: <dark_gray>None"));
            }

            try {
                var registry = ((com.blakube.bktops.plugin.TopAPIImpl)api).getRegistry();
                var internalTop = registry.get(topId).orElse(null);
                if (internalTop instanceof com.blakube.bktops.plugin.top.DefaultTop dTop) {
                    Double val = dTop.getValueProvider().getValue(uuid);
                    if (val == null) {
                        sender.sendMessage(MM.deserialize("  <gray>Value: <red>NULL (Placeholder returned nothing)"));
                    } else {
                        boolean allowZero = dTop.getConfig().isAllowZeroValues();
                        String color = (val == 0.0 && !allowZero) ? "<red>" : "<green>";
                        sender.sendMessage(MM.deserialize("  <gray>Value: " + color + val + (val == 0.0 && !allowZero ? " (Zero not allowed)" : "")));

                        int pos = dTop.getPosition(uuid);
                        if (pos != -1) {
                            sender.sendMessage(MM.deserialize("  <gray>Current Position: <gold>#" + pos));
                        } else {
                            sender.sendMessage(MM.deserialize("  <gray>Current Position: <dark_gray>Not in top"));
                            Optional<Double> min = dTop.getMinValue();
                            if (min.isPresent() && val <= min.get() && dTop.getCurrentSize() >= dTop.getConfig().getSize()) {
                                sender.sendMessage(MM.deserialize("  <gray>Entry status: <red>Value too low to enter (Min: " + min.get() + ")"));
                            } else {
                                sender.sendMessage(MM.deserialize("  <gray>Entry status: <yellow>Should enter on next update"));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                sender.sendMessage(MM.deserialize("  <red>Error fetching details: " + e.getMessage()));
            }
        }
        sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));
        sender.sendMessage(MM.deserialize("<gray>Triggering immediate update..."));
        for (Top top : tops) {
            top.getProcessor().processImmediate(uuid, "Debug command");
        }
    }

    @Subcommand("compare <player1> <player2>")
    public void compare(BukkitCommandActor actor,
                        @Named("player1") String name1,
                        @Named("player2") String name2) {

        CommandSender sender = actor.sender();

       DatabaseExecutors.DB_EXECUTOR.execute(() -> {
            @SuppressWarnings("deprecation") OfflinePlayer op1 = Bukkit.getOfflinePlayer(name1);
            @SuppressWarnings("deprecation") OfflinePlayer op2 = Bukkit.getOfflinePlayer(name2);

            UUID uuid1 = op1.getUniqueId();
            UUID uuid2 = op2.getUniqueId();

            TopAPI api = TopAPIProvider.getInstance();
            @SuppressWarnings("unchecked")
            Collection<Top<UUID>> tops = (Collection<Top<UUID>>) (Collection<?>) api.getAllTops();

            if (tops.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(MM.deserialize("<red>No tops are registered.")));
                return;
            }

            
            record Row(String label, String fmt1, String fmt2, String diff) {}
            List<Row> rows = new ArrayList<>(tops.size());

            for (Top<UUID> top : tops) {
                String topLabel = top.getConfig().getDisplayName() != null
                        ? top.getConfig().getDisplayName()
                        : top.getId();

                int pos1 = top.getPosition(uuid1);
                int pos2 = top.getPosition(uuid2);

                Optional<TopEntry<UUID>> entry1 = pos1 != -1 ? top.getEntry(pos1) : Optional.empty();
                Optional<TopEntry<UUID>> entry2 = pos2 != -1 ? top.getEntry(pos2) : Optional.empty();

                rows.add(new Row(topLabel, formatEntry(top, pos1, entry1), formatEntry(top, pos2, entry2),
                        formatDiff(top, entry1, entry2)));
            }

            
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));
                sender.sendMessage(MM.deserialize(
                        "<gold><bold>BK-Tops</bold></gold> <gray>|</gray> <white>" + name1 + "</white> <gray>vs</gray> <white>" + name2));
                sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));
                for (Row row : rows) {
                    sender.sendMessage(MM.deserialize(
                            "<gray>  " + row.label() + ": <white>" + row.fmt1() + " <gray>vs <white>" + row.fmt2() + row.diff()));
                }
                sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));
            });
        });
    }

    private String formatEntry(Top<UUID> top, int position, Optional<TopEntry<UUID>> entry) {
        if (position == -1 || entry.isEmpty()) return "<red>N/A</red>";

        String value = formatValue(top, entry.get().getValue());
        return "<yellow>#" + position + "</yellow> <white>(" + value + ")</white>";
    }

    private String formatDiff(Top<UUID> top, Optional<TopEntry<UUID>> entry1, Optional<TopEntry<UUID>> entry2) {
        if (entry1.isEmpty() || entry2.isEmpty()) return "";

        double diff = entry1.get().getValue() - entry2.get().getValue();
        if (diff == 0) return " <gray>[=]";

        String formatted = formatValue(top, Math.abs(diff));
        return diff > 0
                ? " <green>[+" + formatted + "]</green>"
                : " <red>[-" + formatted + "]</red>";
    }

    private String formatValue(Top<UUID> top, double value) {
        return TopValueFormatterProvider.isAvailable()
                ? TopValueFormatterProvider.getInstance().resolve(top).format(value)
                : String.valueOf(value);
    }

    private static final int PAGE_SIZE = 10;

    @Subcommand("history export <topId>")
    public void historyExport(BukkitCommandActor actor,
                              @Named("topId") @SuggestWith(AllTopsSuggestions.class) String topId) {
        CommandSender sender = actor.sender();
        HistoryRegistry registry = HistoryRegistry.getInstance();
        if (registry == null) {
            sender.sendMessage(MM.deserialize("<red>History registry is not available."));
            return;
        }

        TopAPI api = TopAPIProvider.getInstance();
        @SuppressWarnings("unchecked")
        Top<UUID> top = api.getTop(topId);
        if (top == null) {
            notifyService.sendChat(sender, "message.top-not-found");
            return;
        }

        List<TopEntry<UUID>> entries = top.getEntries();
        if (entries.isEmpty()) {
            sender.sendMessage(MM.deserialize("<yellow>Top <white>" + topId + "</white> has no entries to export."));
            return;
        }

        registry.writeExport(top.getId(), entries);
        sender.sendMessage(MM.deserialize("<green>Exported <yellow>" + entries.size()
                + "</yellow> entries of <yellow>" + topId + "</yellow> to <white>history/exports</white>."));
    }

    @Subcommand("history list <type>")
    public void historyList(BukkitCommandActor actor,
                            @Named("type") @SuggestWith(TypeSuggestions.class) String type,
                            @revxrsal.commands.annotation.Optional @Named("topId") @SuggestWith(HistoryTopIdSuggestions.class) String topId) {
        CommandSender sender = actor.sender();
        HistoryRegistry registry = HistoryRegistry.getInstance();
        if (registry == null) {
            sender.sendMessage(MM.deserialize("<red>History registry is not available."));
            return;
        }
        if (!HistoryRegistry.isValidType(type)) {
            sender.sendMessage(MM.deserialize("<red>Type must be <white>reset</white> or <white>exports</white>."));
            return;
        }

        if (topId == null || topId.isBlank()) {
            List<String> tops = registry.listTopIds(type);
            if (tops.isEmpty()) {
                sender.sendMessage(MM.deserialize("<yellow>No <white>" + type + "</white> history recorded yet."));
                return;
            }
            sender.sendMessage(MM.deserialize("<gold><bold>History</bold></gold> <gray>|</gray> <white>" + type + " — tops"));
            for (String id : tops) {
                int count = registry.listDates(type, id).size();
                sender.sendMessage(MM.deserialize("<gray>  <yellow>" + id + "</yellow> <gray>(" + count + " file(s))"));
            }
            sender.sendMessage(MM.deserialize("<gray>Use <white>/bktops history list " + type + " <topId></white> to see dates."));
            return;
        }

        List<String> dates = registry.listDates(type, topId);
        if (dates.isEmpty()) {
            sender.sendMessage(MM.deserialize("<yellow>No <white>" + type + "</white> history found for <white>" + topId + "</white>."));
            return;
        }
        sender.sendMessage(MM.deserialize("<gold><bold>History</bold></gold> <gray>|</gray> <white>" + type + " — " + topId));
        for (String date : dates) {
            sender.sendMessage(MM.deserialize("<gray>  <yellow>" + date));
        }
        sender.sendMessage(MM.deserialize("<gray>Use <white>/bktops history show " + type + " " + topId + " <date> [page]</white>."));
    }

    @Subcommand("history show <type> <topId> <date>")
    public void historyShow(BukkitCommandActor actor,
                            @Named("type") @SuggestWith(TypeSuggestions.class) String type,
                            @Named("topId") @SuggestWith(HistoryTopIdSuggestions.class) String topId,
                            @Named("date") @SuggestWith(DateSuggestions.class) String date,
                            @revxrsal.commands.annotation.Optional @Named("page") Integer page) {
        CommandSender sender = actor.sender();
        HistoryRegistry registry = HistoryRegistry.getInstance();
        if (registry == null) {
            sender.sendMessage(MM.deserialize("<red>History registry is not available."));
            return;
        }
        if (!HistoryRegistry.isValidType(type)) {
            sender.sendMessage(MM.deserialize("<red>Type must be <white>reset</white> or <white>exports</white>."));
            return;
        }

        YamlConfiguration yaml = registry.read(type, topId, date);
        if (yaml == null) {
            sender.sendMessage(MM.deserialize("<red>No <white>" + type + "</white> history for <white>" + topId + "</white> at <white>" + date + "</white>."));
            return;
        }

        List<Map<?, ?>> entries = yaml.getMapList("entries");
        if (entries.isEmpty()) {
            sender.sendMessage(MM.deserialize("<yellow>That file has no entries."));
            return;
        }

        int totalPages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int current = page == null ? 1 : Math.max(1, Math.min(page, totalPages));
        int from = (current - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());

        String resetDate = yaml.getString("reset-date", date);
        sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));
        sender.sendMessage(MM.deserialize("<gold><bold>" + topId + "</bold></gold> <gray>|</gray> <white>" + resetDate
                + "</white> <gray>(page " + current + "/" + totalPages + ")"));
        sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));
        for (int i = from; i < to; i++) {
            Map<?, ?> row = entries.get(i);
            Object pos = row.get("position");
            Object name = row.get("name");
            Object value = row.get("value");
            sender.sendMessage(MM.deserialize("<gray>  <gold>#" + pos + "</gold> <white>" + name + "</white> <gray>— <yellow>" + value));
        }
        if (current < totalPages) {
            sender.sendMessage(MM.deserialize("<gray>Next: <white>/bktops history show " + type + " " + topId + " " + date + " " + (current + 1)));
        }
        sender.sendMessage(MM.deserialize("<gray>──────────────────────────────"));
    }

    public static final class TypeSuggestions implements SuggestionProvider<BukkitCommandActor> {
        @Override
        public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            return List.of(HistoryRegistry.TYPE_RESET, HistoryRegistry.TYPE_EXPORTS);
        }
    }

    public static final class HistoryTopIdSuggestions implements SuggestionProvider<BukkitCommandActor> {
        @Override
        public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            HistoryRegistry registry = HistoryRegistry.getInstance();
            if (registry == null) return List.of();
            String type = context.getResolvedArgumentOrNull("type");
            if (HistoryRegistry.isValidType(type)) return registry.listTopIds(type);
            List<String> all = new ArrayList<>(registry.listTopIds(HistoryRegistry.TYPE_RESET));
            all.addAll(registry.listTopIds(HistoryRegistry.TYPE_EXPORTS));
            return all;
        }
    }

    public static final class DateSuggestions implements SuggestionProvider<BukkitCommandActor> {
        @Override
        public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            HistoryRegistry registry = HistoryRegistry.getInstance();
            if (registry == null) return List.of();
            String type = context.getResolvedArgumentOrNull("type");
            String topId = context.getResolvedArgumentOrNull("topId");
            if (!HistoryRegistry.isValidType(type) || topId == null) return List.of();
            return registry.listDates(type, topId);
        }
    }

    public static final class AllTopsSuggestions implements SuggestionProvider<BukkitCommandActor> {
        @Override
        public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            if (!TopAPIProvider.isAvailable()) return List.of();
            List<String> ids = new ArrayList<>();
            for (Top top : TopAPIProvider.getInstance().getAllTops()) ids.add(top.getId());
            return ids;
        }
    }

    private Configuration getTopsConfiguration(CommandSender sender) {
        var container = plugin.getConfigService().provide(ConfigType.TOPS);
        if (!(container instanceof ConfigContainerImpl impl)) {
            sender.sendMessage(MM.deserialize("<red>Internal tops config type is not supported."));
            return null;
        }
        return impl.getInternalConfiguration();
    }

    private int nextNumericKey(ConfigurationSection section) {
        int max = -1;
        for (String key : section.getKeys(false)) {
            try {
                max = Math.max(max, Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        return max + 1;
    }
}
