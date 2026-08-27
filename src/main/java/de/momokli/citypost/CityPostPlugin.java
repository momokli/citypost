package de.momokli.citypost;

import de.momokli.citypost.command.CityMailCommand;
import de.momokli.citypost.command.CityPostCommand;
import de.momokli.citypost.command.CitySendCommand;
import de.momokli.citypost.config.CityPostConfig;
import de.momokli.citypost.config.Messages;
import de.momokli.citypost.db.Database;
import de.momokli.citypost.db.PackageDao;
import de.momokli.citypost.receive.MailGui;
import de.momokli.citypost.receive.ReceiveManager;
import de.momokli.citypost.send.BackpackGui;
import de.momokli.citypost.send.SendManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * CityPost - One-way Item-Package-System (World 1/2 → City).
 *
 * <p>Ein Plugin, zwei Modi (config.yml {@code mode: SEND|RECEIVE}):
 * SEND-Server schreiben Pakete in die MariaDB, RECEIVE-Server lesen sie.
 * Es gibt keinen Rückkanal: nur SEND-Server führen Item-Inserts aus.</p>
 */
public final class CityPostPlugin extends JavaPlugin {

    private CityPostMode mode;
    private CityPostConfig config;
    private Messages messages;
    private Database database;
    private PackageDao packageDao;
    private SendManager sendManager;
    private ReceiveManager receiveManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        mode = CityPostMode.fromString(getConfig().getString("mode", "SEND"));
        if (mode == null) {
            getLogger().severe("Ungültiger Wert für 'mode' in config.yml (erwartet SEND oder RECEIVE). Plugin wird deaktiviert.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        config = CityPostConfig.from(getConfig());
        messages = Messages.load(getDataFolder().toPath(), config.language(), getClassLoader(), getLogger());

        // Diagnose-Befehl registrieren, bevor die DB-Init entscheidet (funktioniert in beiden Modi)
        PluginCommand adminCommand = getCommand("citypost");
        if (adminCommand != null) {
            adminCommand.setExecutor(new CityPostCommand(this));
        }

        try {
            database = new Database(config.jdbcUrl(), config.dbUser(), config.dbPassword(), config.poolSize());
            database.initSchema();
            packageDao = new PackageDao(database.dataSource());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Datenbank-Initialisierung fehlgeschlagen (" + e.getMessage() + "). Plugin wird deaktiviert.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (mode == CityPostMode.SEND) {
            getLogger().info("Modus SEND: /citysend aktiv, /citymail ist auf diesem Server nicht vorhanden.");
            sendManager = new SendManager(this);
            enableCommand("citysend", new CitySendCommand(this));
            removeCommand("citymail");
        } else {
            getLogger().info("Modus RECEIVE: /citymail aktiv, /citysend ist auf diesem Server nicht vorhanden.");
            receiveManager = new ReceiveManager(this);
            receiveManager.start();
            enableCommand("citymail", new CityMailCommand(this));
            removeCommand("citysend");
        }

        getLogger().info("CityPost v" + getPluginMeta().getVersion() + " aktiv (Modus: " + mode + ").");
    }

    @Override
    public void onDisable() {
        if (receiveManager != null) {
            receiveManager.stop();
        }
        closeOpenGuis();
        if (database != null) {
            database.close();
        }
    }

    private void closeOpenGuis() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof BackpackGui || holder instanceof MailGui) {
                player.closeInventory();
            }
        }
    }

    private void enableCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        }
    }

    /**
     * Entfernt den Befehl komplett aus der CommandMap, damit er auf dieser
     * Server-Seite nicht existiert ("Kein Rückkanal" / kein /citysend auf RECEIVE).
     */
    private void removeCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.unregister(Bukkit.getCommandMap());
        }
    }

    public CityPostMode getMode() {
        return mode;
    }

    public CityPostConfig getConfigData() {
        return config;
    }

    public Messages getMessages() {
        return messages;
    }

    public PackageDao getPackageDao() {
        return packageDao;
    }

    public SendManager getSendManager() {
        return sendManager;
    }

    public ReceiveManager getReceiveManager() {
        return receiveManager;
    }
}
