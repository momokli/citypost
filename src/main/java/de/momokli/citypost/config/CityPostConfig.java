package de.momokli.citypost.config;

import de.momokli.citypost.CityPostMode;
import org.bukkit.configuration.Configuration;

/**
 * Gekapselte, validierte Sicht auf die {@code config.yml}.
 */
public record CityPostConfig(
        CityPostMode mode,
        String serverTag,
        String dbHost,
        int dbPort,
        String dbName,
        String dbUser,
        String dbPassword,
        int poolSize,
        int backpackSize,
        int deliveryDelay,
        int pollInterval,
        int cooldownSeconds,
        String language
) {

    public static CityPostConfig from(Configuration c) {
        String host = c.getString("database.host", "localhost");
        int port = c.getInt("database.port", 3306);
        String name = c.getString("database.name", "citypost");
        String user = c.getString("database.user", "citypost");
        String password = c.getString("database.password", "CHANGE_ME");
        int poolSize = c.getInt("database.pool-size", 4);
        return new CityPostConfig(
                CityPostMode.fromString(c.getString("mode", "SEND")),
                c.getString("server-tag", "world1"),
                host, port, name, user, password,
                Math.max(1, poolSize),
                Math.max(9, c.getInt("backpack-size", 27)),
                Math.max(0, c.getInt("delivery-delay", 0)),
                Math.max(1, c.getInt("poll-interval", 10)),
                Math.max(0, c.getInt("cooldown-seconds", 30)),
                c.getString("language", "de")
        );
    }

    public String jdbcUrl() {
        return "jdbc:mariadb://" + dbHost + ":" + dbPort + "/" + dbName
                + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true";
    }
}
