package de.momokli.citypost.receive;

import de.momokli.citypost.CityPostPlugin;
import de.momokli.citypost.config.Messages;
import de.momokli.citypost.db.CityPackage;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Empfänger-Logik (Modus RECEIVE): Pollt offene Pakete (~10 s), hält den
 * GUI-Cache und wickelt das Abholen (Claim) ab. Schreibt ausschließlich das
 * Claim-Flag — niemals Item-Daten (One-way).
 */
public final class ReceiveManager implements Listener {

    private final CityPostPlugin plugin;
    private volatile List<CityPackage> cache = List.of();
    private final Map<Integer, Integer> stackCounts = new ConcurrentHashMap<>();
    private final Set<Integer> claimedInFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, MailGui> openGuis = new ConcurrentHashMap<>();
    private int pollTaskId = -1;

    public ReceiveManager(CityPostPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        refresh();
        long intervalTicks = plugin.getConfigData().pollInterval() * 20L;
        pollTaskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::refresh, intervalTicks, intervalTicks)
                .getTaskId();
    }

    public void stop() {
        if (pollTaskId != -1) {
            Bukkit.getScheduler().cancelTask(pollTaskId);
            pollTaskId = -1;
        }
    }

    /** Asynchroner Poll: offene Pakete laden + Stack-Zahlen vorberechnen. */
    public void refresh() {
        try {
            List<CityPackage> fresh = plugin.getPackageDao().findUnclaimed();
            for (CityPackage pkg : fresh) {
                stackCounts.computeIfAbsent(pkg.id(), id -> countStacks(pkg.itemsBlob()));
            }
            cache = List.copyOf(fresh);
        } catch (Exception e) {
            plugin.getLogger().warning("Poll fehlgeschlagen: " + e.getMessage());
        }
    }

    private int countStacks(String blob) {
        try {
            return ItemCodec.decode(blob).size();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Paket-Blob nicht lesbar (ID unbekannt): " + e.getMessage());
            return -1;
        }
    }

    public List<CityPackage> getPackages() {
        return cache;
    }

    public int stackCount(CityPackage pkg) {
        return stackCounts.getOrDefault(pkg.id(), -1);
    }

    public void openMail(Player player) {
        MailGui gui = new MailGui(plugin, player, this);
        openGuis.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    /** Abholen: async Claim-Flag setzen, dann Items ins Inventar (Überlauf droppen). */
    public void claim(Player player, CityPackage pkg, MailGui gui) {
        Messages messages = plugin.getMessages();
        if (!claimedInFlight.add(pkg.id())) {
            return; // Doppelklick-Schutz
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean updated = plugin.getPackageDao().markClaimed(pkg.id());
                List<ItemStack> items = updated ? ItemCodec.decode(pkg.itemsBlob()) : List.of();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    claimedInFlight.remove(pkg.id());
                    if (!updated) {
                        player.sendMessage(messages.component("msg.mail.already-claimed"));
                        return;
                    }
                    stackCounts.remove(pkg.id());
                    cache = cache.stream().filter(p -> p.id() != pkg.id()).toList();
                    int dropped = giveItems(player, items);
                    player.sendMessage(messages.component("msg.mail.claimed", "id", pkg.id()));
                    if (dropped > 0) {
                        player.sendMessage(messages.component("msg.mail.inventory-full", "count", dropped));
                    }
                    MailGui open = openGuis.get(player.getUniqueId());
                    if (open != null) {
                        open.render();
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Abholen von Paket #" + pkg.id() + " fehlgeschlagen: " + e.getMessage());
                claimedInFlight.remove(pkg.id());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(messages.component("msg.mail.claim-failed")));
            }
        });
    }

    private int giveItems(Player player, List<ItemStack> items) {
        int dropped = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            dropped += player.getInventory().addItem(item).values().stream()
                    .mapToInt(ItemStack::getAmount)
                    .peek(ignored -> player.getWorld().dropItemNaturally(player.getLocation(), item))
                    .sum();
        }
        return dropped;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MailGui gui)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= top.getSize()) {
            return; // eigenes Inventar bleibt bedienbar
        }
        event.setCancelled(true);
        gui.handleClick(player, rawSlot, this);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MailGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getInventory();
        if (top.getHolder() instanceof MailGui) {
            openGuis.remove(player.getUniqueId());
        }
    }
}
