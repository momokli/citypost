package de.momokli.citypost.command;

import de.momokli.citypost.CityPostPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /citysend} — öffnet das Backpack-GUI (nur Modus SEND registriert).
 */
public final class CitySendCommand implements CommandExecutor {

    private final CityPostPlugin plugin;

    public CitySendCommand(CityPostPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().component("msg.error.console"));
            return true;
        }
        if (!player.hasPermission("citypost.send")) {
            player.sendMessage(plugin.getMessages().component("msg.error.no-permission"));
            return true;
        }
        plugin.getSendManager().openBackpack(player);
        return true;
    }
}
