package com.blakube.bktops.plugin.command;

import com.blakube.bktops.plugin.BKTops;
import com.blakube.bktops.plugin.service.config.ConfigService;
import com.blakube.bktops.plugin.service.notify.NotifyService;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command("bktops")
@CommandPermission("bk-tops.admin")
public class BKTopsCommand {

    private final NotifyService notifyService;
    private final BKTops plugin;

    public BKTopsCommand(NotifyService notifyService, BKTops plugin) {
        this.notifyService = notifyService;
        this.plugin = plugin;
    }

    @Subcommand("reload")
    public void reload(BukkitCommandActor actor) {
        plugin.reloadPlugin();
        notifyService.sendChat(actor.sender(), "message.config-reloaded");
    }

}
