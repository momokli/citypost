package de.momokli.citypost.receive;

import de.momokli.citypost.CityPostPlugin;
import de.momokli.citypost.config.Messages;
import de.momokli.citypost.db.CityPackage;
import de.momokli.citypost.util.Pager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Paginiertes Postfach-GUI (36 Slots: 27 Paket-Slots + Navigationsreihe).
 * Ein Paket wird als Chest-Item dargestellt, Klick = abholen.
 */
public final class MailGui implements InventoryHolder {

    public static final int SIZE = 36;
    public static final int PACKAGES_PER_PAGE = 27;
    private static final int PREV_SLOT = 27;
    private static final int REFRESH_SLOT = 31;
    private static final int NEXT_SLOT = 35;

    private final CityPostPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final ReceiveManager manager;
    private int page;

    public MailGui(CityPostPlugin plugin, Player player, ReceiveManager manager) {
        this.plugin = plugin;
        this.player = player;
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().component("gui.mail.title"));
        render();
    }

    public void render() {
        Messages messages = plugin.getMessages();
        inventory.clear();

        List<CityPackage> packages = manager.getPackages();
        int pages = Pager.pageCount(packages.size(), PACKAGES_PER_PAGE);
        page = Pager.clampPage(page, packages.size(), PACKAGES_PER_PAGE);

        for (int slot = 0; slot < PACKAGES_PER_PAGE; slot++) {
            int index = page * PACKAGES_PER_PAGE + slot;
            if (index < packages.size()) {
                inventory.setItem(slot, packageItem(messages, packages.get(index)));
            }
        }

        inventory.setItem(PREV_SLOT, navItem(messages.component("gui.mail.prev"), Material.ARROW));
        inventory.setItem(NEXT_SLOT, navItem(messages.component("gui.mail.next"), Material.ARROW));
        inventory.setItem(REFRESH_SLOT, navItem(
                messages.component("gui.mail.refresh"),
                messages.component("gui.mail.refresh-lore", "page", page + 1, "pages", pages),
                Material.COMPASS));
        for (int slot = 28; slot <= 34; slot++) {
            if (slot != REFRESH_SLOT) {
                inventory.setItem(slot, pane());
            }
        }
    }

    public void handleClick(Player clicker, int slot, ReceiveManager manager) {
        Messages messages = plugin.getMessages();
        List<CityPackage> packages = manager.getPackages();
        if (slot == PREV_SLOT && page > 0) {
            page--;
            render();
        } else if (slot == NEXT_SLOT && page < Pager.pageCount(packages.size(), PACKAGES_PER_PAGE) - 1) {
            page++;
            render();
        } else if (slot == REFRESH_SLOT) {
            manager.refresh();
            render();
            clicker.sendMessage(messages.component("msg.mail.refreshed"));
        } else if (slot >= 0 && slot < PACKAGES_PER_PAGE) {
            int index = page * PACKAGES_PER_PAGE + slot;
            if (index < packages.size()) {
                manager.claim(clicker, packages.get(index), this);
            }
        }
    }

    private ItemStack packageItem(Messages messages, CityPackage pkg) {
        int stacks = manager.stackCount(pkg);
        String stacksText = stacks >= 0 ? String.valueOf(stacks) : "?";
        ItemStack item = new ItemStack(Material.CHEST);
        item.editMeta(meta -> {
            meta.displayName(messages.component("gui.mail.package-name", "id", pkg.id()));
            meta.lore(List.of(messages.component(
                    "gui.mail.package-lore",
                    "stacks", stacksText,
                    "world", pkg.world(),
                    "age", age(messages, pkg.createdAt()))));
        });
        return item;
    }

    private static String age(Messages messages, Instant createdAt) {
        if (createdAt == null) {
            return messages.raw("msg.age.now");
        }
        long seconds = Duration.between(createdAt, Instant.now()).getSeconds();
        if (seconds < 60) {
            return messages.raw("msg.age.now");
        }
        if (seconds < 3600) {
            return messages.format("msg.age.min", "n", seconds / 60);
        }
        if (seconds < 86400) {
            return messages.format("msg.age.h", "n", seconds / 3600);
        }
        return messages.format("msg.age.d", "n", seconds / 86400);
    }

    private static ItemStack navItem(Component name, Material material) {
        return navItem(name, null, material);
    }

    private static ItemStack navItem(Component name, Component lore, Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name);
            if (lore != null) {
                meta.lore(List.of(lore));
            }
        });
        return item;
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.text(" ")));
        return pane;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}
