package de.momokli.citypost.send;

import de.momokli.citypost.CityPostPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Stardew-ähnliches Backpack-GUI (Standard: 27 Slots).
 *
 * <p>Obere {@code size-9} Slots sind nutzbar, die letzte Reihe enthält
 * „Abschicken" (Emerald Block) und „Abbrechen" (Barrier).</p>
 */
public final class BackpackGui implements InventoryHolder {

    /** Abstand vom Ende der GUI zum Abschicken-Button (27 → Slot 22). */
    public static final int SEND_BUTTON_OFFSET = 5;
    /** Abstand vom Ende der GUI zum Abbrechen-Button (27 → Slot 24). */
    public static final int CANCEL_BUTTON_OFFSET = 3;

    private final Inventory inventory;
    private final Player player;
    private final int usableSlots;
    private boolean sending;
    private boolean completed;

    public BackpackGui(CityPostPlugin plugin, Player player, int size) {
        this.player = player;
        this.usableSlots = size - 9;
        this.inventory = Bukkit.createInventory(this, size, plugin.getMessages().component("gui.backpack.title"));
        fillBottomRow(plugin);
    }

    private void fillBottomRow(CityPostPlugin plugin) {
        for (int slot = usableSlots; slot < inventory.getSize(); slot++) {
            if (slot == inventory.getSize() - SEND_BUTTON_OFFSET) {
                inventory.setItem(slot, item(Material.EMERALD_BLOCK,
                        plugin.getMessages().component("gui.backpack.send"),
                        List.of(plugin.getMessages().component("gui.backpack.send-lore"))));
            } else if (slot == inventory.getSize() - CANCEL_BUTTON_OFFSET) {
                inventory.setItem(slot, item(Material.BARRIER,
                        plugin.getMessages().component("gui.backpack.cancel"),
                        List.of(plugin.getMessages().component("gui.backpack.cancel-lore"))));
            } else {
                inventory.setItem(slot, pane());
            }
        }
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
        });
        return stack;
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.text(" ")));
        return pane;
    }

    public List<ItemStack> collectItems() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < usableSlots; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        return items;
    }

    public void clearItems() {
        for (int slot = 0; slot < usableSlots; slot++) {
            inventory.setItem(slot, null);
        }
    }

    public boolean isBottomRowSlot(int rawSlot) {
        return rawSlot >= usableSlots && rawSlot < inventory.getSize();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isSending() {
        return sending;
    }

    public void setSending(boolean sending) {
        this.sending = sending;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
