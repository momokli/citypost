package de.momokli.citypost.send;

import de.momokli.citypost.CityPostPlugin;
import de.momokli.citypost.config.Messages;
import de.momokli.citypost.items.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sender-Logik (Modus SEND): Cooldown, Backpack-GUI-Steuerung,
 * Serialisierung und asynchroner MariaDB-INSERT.
 */
public final class SendManager implements Listener {

    private final CityPostPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BackpackGui> openGuis = new HashMap<>();

    public SendManager(CityPostPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openBackpack(Player player) {
        Messages messages = plugin.getMessages();
        long remaining = remainingCooldown(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(messages.component("msg.send.cooldown", "seconds", remaining));
            return;
        }
        BackpackGui gui = new BackpackGui(plugin, player, plugin.getConfigData().backpackSize());
        openGuis.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    private long remainingCooldown(UUID playerId) {
        Long lastSent = cooldowns.get(playerId);
        if (lastSent == null) {
            return 0;
        }
        long cooldownMillis = plugin.getConfigData().cooldownSeconds() * 1000L;
        if (cooldownMillis <= 0) {
            return 0;
        }
        long remaining = cooldownMillis - (System.currentTimeMillis() - lastSent);
        return remaining > 0 ? (remaining / 1000) + 1 : 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BackpackGui gui)) {
            return;
        }
        if (!gui.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (gui.isSending()) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= top.getSize()) {
            // Klick im eigenen Inventar: normal erlauben (Shift-Klick legt Items in freie Slots)
            return;
        }
        if (gui.isBottomRowSlot(rawSlot)) {
            event.setCancelled(true);
            int size = gui.getInventory().getSize();
            if (rawSlot == size - BackpackGui.SEND_BUTTON_OFFSET) {
                send(player, gui);
            } else if (rawSlot == size - BackpackGui.CANCEL_BUTTON_OFFSET) {
                cancel(player, gui);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BackpackGui gui)) {
            return;
        }
        if (gui.isSending()) {
            event.setCancelled(true);
            return;
        }
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize() && gui.isBottomRowSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof BackpackGui gui)) {
            return;
        }
        openGuis.remove(player.getUniqueId());
        if (!gui.isCompleted()) {
            // Items zurückgeben, wenn das Paket nicht abgeschickt wurde
            returnItems(player, gui.collectItems());
            gui.clearItems();
        }
    }

    private void send(Player player, BackpackGui gui) {
        Messages messages = plugin.getMessages();
        List<ItemStack> items = gui.collectItems();
        if (items.isEmpty()) {
            player.sendMessage(messages.component("msg.send.no-items"));
            return;
        }

        gui.setSending(true); // GUI sperren, solange der INSERT läuft
        String blob;
        try {
            blob = ItemCodec.encode(items);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Item-Serialisierung fehlgeschlagen: " + e.getMessage());
            player.sendMessage(messages.component("msg.send.failed"));
            gui.setSending(false);
            return;
        }

        UUID playerId = player.getUniqueId();
        String world = plugin.getConfigData().serverTag();
        cooldowns.put(playerId, System.currentTimeMillis());

        Runnable insert = () -> {
            try {
                int packageId = plugin.getPackageDao().insert(playerId.toString(), blob, world);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    gui.clearItems();
                    gui.setCompleted(true);
                    player.closeInventory();
                    player.sendMessage(messages.component("msg.send.on-way", "id", packageId));
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Paket-INSERT fehlgeschlagen: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    gui.setSending(false);
                    player.sendMessage(messages.component("msg.send.failed"));
                });
            }
        };

        long delayTicks = plugin.getConfigData().deliveryDelay() * 20L;
        if (delayTicks > 0) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, insert, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, insert);
        }
    }

    private void cancel(Player player, BackpackGui gui) {
        gui.setSending(true); // verhindert doppeltes Zurückgeben im Close-Handler
        gui.setCompleted(true);
        returnItems(player, gui.collectItems());
        gui.clearItems();
        player.closeInventory();
    }

    private void returnItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(item)
                    .forEach((slot, overflow) -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
        }
    }
}
