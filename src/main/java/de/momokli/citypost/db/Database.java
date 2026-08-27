package de.momokli.citypost.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * HikariCP-Pool auf die MariaDB/MySQL-Datenbank plus Schema-Initialisierung.
 */
public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;

    public Database(String jdbcUrl, String user, String password, int poolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(Math.max(1, poolSize));
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(5000);
        hikariConfig.setPoolName("citypost");
        // Explizit den gebündelten MariaDB-Treiber verwenden (nicht den Server-eigenen mysql-connector-j);
        // H2 (Tests) behält die Auto-Erkennung.
        if (jdbcUrl.startsWith("jdbc:mysql:") || jdbcUrl.startsWith("jdbc:mariadb:")) {
            hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        }
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * Legt die Tabelle {@code city_packages} an (idempotent).
     * Bei H2 (Tests) wird LONGBLOB durch BLOB ersetzt.
     */
    public void initSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
            String blobType = product.contains("h2") ? "BLOB" : "LONGBLOB";
            String sql = "CREATE TABLE IF NOT EXISTS city_packages (" +
                    " id INT AUTO_INCREMENT PRIMARY KEY," +
                    " player_uuid CHAR(36) NOT NULL," +
                    " items_blob " + blobType + " NOT NULL," +
                    " world VARCHAR(32) NOT NULL," +
                    " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    " claimed TINYINT(1) DEFAULT 0" +
                    ")";
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
