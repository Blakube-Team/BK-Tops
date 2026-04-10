package com.blakube.bktops.plugin.command;

import com.blakube.bktops.api.TopAPI;
import com.blakube.bktops.api.TopAPIProvider;
import com.blakube.bktops.api.top.Top;
import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.BKTops;
import com.blakube.bktops.plugin.formatter.NumberFormatterProvider;
import com.blakube.bktops.plugin.service.notify.NotifyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Command("bktops")
@CommandPermission("bk-tops.admin")
public class BKTopsCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final NotifyService notifyService;
    private final BKTops plugin;

    public BKTopsCommand(NotifyService notifyService, BKTops plugin) {
        this.notifyService = notifyService;
        this.plugin = plugin;
    }

    @Subcommand("reset <topId>")
    public void reset(BukkitCommandActor actor, @Named("topId") String topId) {
        TopAPI api = TopAPIProvider.getInstance();
        var top = api.getTop(topId);

        if (top == null) {
            notifyService.sendChat(actor.sender(), "messages. top-not-found");
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

    @Subcommand("compare <player1> <player2>")
    public void compare(BukkitCommandActor actor,
                        @Named("player1") String name1,
                        @Named("player2") String name2) {

        CommandSender sender = actor.sender();

        @SuppressWarnings("deprecation")
        OfflinePlayer op1 = Bukkit.getOfflinePlayer(name1);
        @SuppressWarnings("deprecation")
        OfflinePlayer op2 = Bukkit.getOfflinePlayer(name2);

        UUID uuid1 = op1.getUniqueId();
        UUID uuid2 = op2.getUniqueId();

        TopAPI api = TopAPIProvider.getInstance();
        @SuppressWarnings("unchecked")
        Collection<Top<UUID>> tops = (Collection<Top<UUID>>) (Collection<?>) api.getAllTops();

        if (tops.isEmpty()) {
            sender.sendMessage(MM.deserialize("<red>No tops are registered."));
            return;
        }

        sender.sendMessage(MM.deserialize(
                "<gray>──────────────────────────────"));
        sender.sendMessage(MM.deserialize(
                "<gold><bold>BK-Tops</bold></gold> <gray>|</gray> <white>" + name1 + "</white> <gray>vs</gray> <white>" + name2));
        sender.sendMessage(MM.deserialize(
                "<gray>──────────────────────────────"));

        for (Top<UUID> top : tops) {
            String topLabel = top.getConfig().getDisplayName() != null
                    ? top.getConfig().getDisplayName()
                    : top.getId();

            int pos1 = top.getPosition(uuid1);
            int pos2 = top.getPosition(uuid2);

            Optional<TopEntry<UUID>> entry1 = pos1 != -1 ? top.getEntry(pos1) : Optional.empty();
            Optional<TopEntry<UUID>> entry2 = pos2 != -1 ? top.getEntry(pos2) : Optional.empty();

            String fmt1 = formatEntry(pos1, entry1);
            String fmt2 = formatEntry(pos2, entry2);

            String diff = formatDiff(entry1, entry2);

            sender.sendMessage(MM.deserialize(
                    "<gray>  " + topLabel + ": <white>" + fmt1 + " <gray>vs <white>" + fmt2 + diff));
        }

        sender.sendMessage(MM.deserialize(
                "<gray>──────────────────────────────"));
    }

    private String formatEntry(int position, Optional<TopEntry<UUID>> entry) {
        if (position == -1 || entry.isEmpty()) return "<red>N/A</red>";

        String value = NumberFormatterProvider.isAvailable()
                ? NumberFormatterProvider.getInstance().format(entry.get().getValue())
                : String.valueOf(entry.get().getValue());

        return "<yellow>#" + position + "</yellow> <white>(" + value + ")</white>";
    }

    private String formatDiff(Optional<TopEntry<UUID>> entry1, Optional<TopEntry<UUID>> entry2) {
        if (entry1.isEmpty() || entry2.isEmpty()) return "";

        double diff = entry1.get().getValue() - entry2.get().getValue();
        if (diff == 0) return " <gray>[=]";

        String formatted = NumberFormatterProvider.isAvailable()
                ? NumberFormatterProvider.getInstance().format(Math.abs(diff))
                : String.valueOf(Math.abs(diff));

        return diff > 0
                ? " <green>[+" + formatted + "]</green>"
                : " <red>[-" + formatted + "]</red>";
    }
}
