package de.momokli.citypost.command;

import de.momokli.citypost.CityPostPlugin;
import de.momokli.citypost.items.ItemCodec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /citypost selftest} — Konsolen-/Admin-Diagnose: prüft ItemCodec-
 * Roundtrip und den echten MariaDB-Pfad (INSERT → SELECT → Claim) ohne Spieler.
 */
public final class CityPostCommand implements CommandExecutor {

    private static final String TEST_UUID = "00000000-0000-0000-0000-000000000000";

    private final CityPostPlugin plugin;

    public CityPostCommand(CityPostPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("selftest")) {
            sender.sendMessage(plugin.getMessages().component("msg.admin.usage"));
            return true;
        }
        if (sender instanceof Player player && !player.hasPermission("citypost.admin")) {
            player.sendMessage(plugin.getMessages().component("msg.error.no-permission"));
            return true;
        }
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("citypost.admin")) {
            sender.sendMessage(plugin.getMessages().component("msg.error.no-permission"));
            return true;
        }
        runSelftest(sender);
        return true;
    }

    private void runSelftest(CommandSender sender) {
        sender.sendMessage(plugin.getMessages().component("msg.admin.selftest-start"));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 5);
        sword.editMeta(meta -> meta.displayName(Component.text("Selftest-Schwert")));
        List<ItemStack> items = List.of(sword, new ItemStack(Material.STONE, 64));

        String itemResult;
        try {
            String blob = ItemCodec.encode(items);
            List<ItemStack> restored = ItemCodec.decode(blob);
            boolean ok = restored.size() == 2
                    && restored.get(0).getType() == Material.DIAMOND_SWORD
                    && restored.get(0).getEnchantmentLevel(Enchantment.SHARPNESS) == 5
                    && "Selftest-Schwert".equals(restored.get(0).getItemMeta().getDisplayName())
                    && restored.get(1).getType() == Material.STONE
                    && restored.get(1).getAmount() == 64;
            itemResult = ok ? "ItemCodec: PASS" : "ItemCodec: FAIL (Roundtrip inkonsistent)";
        } catch (RuntimeException e) {
            itemResult = "ItemCodec: FAIL (" + e.getMessage() + ")";
        }
        sender.sendMessage(plugin.getMessages().component("msg.admin.selftest-item", "result", itemResult));

        final String blob;
        try {
            blob = ItemCodec.encode(items);
        } catch (RuntimeException e) {
            sender.sendMessage(plugin.getMessages().component("msg.admin.selftest-db", "result", "DB-Skip (kein Blob)"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = dbSelftestResult(blob);
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(plugin.getMessages().component("msg.admin.selftest-db", "result", result)));
        });
    }

    private String dbSelftestResult(String blob) {
        try {
            int id = plugin.getPackageDao().insert(TEST_UUID, blob, "selftest");
            boolean found = plugin.getPackageDao().findUnclaimed().stream().anyMatch(p -> p.id() == id);
            boolean claimed = plugin.getPackageDao().markClaimed(id);
            boolean gone = plugin.getPackageDao().findUnclaimed().stream().noneMatch(p -> p.id() == id);
            return (found && claimed && gone)
                    ? "DAO/MariaDB: PASS (Paket #" + id + " inserted, found, claimed)"
                    : "DAO/MariaDB: FAIL (found=" + found + ", claimed=" + claimed + ", gone=" + gone + ")";
        } catch (Exception e) {
            return "DAO/MariaDB: FAIL (" + e.getMessage() + ")";
        }
    }
}
