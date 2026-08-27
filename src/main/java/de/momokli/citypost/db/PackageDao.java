package de.momokli.citypost.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Datenzugriff auf {@code city_packages}.
 *
 * <p>One-way garantiert: Es gibt ausschließlich INSERT (SEND-Seite),
 * SELECT unclaimed und Claim-Flag-Update (RECEIVE-Seite). Kein Löschen,
 * kein Update von Item-Daten, kein Rückkanal.</p>
 */
public final class PackageDao {

    private final DataSource dataSource;

    public PackageDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Nur SEND-Server: Paket einfügen. Liefert die neue ID (oder -1). */
    public int insert(String playerUuid, String itemsBlob, String world) throws SQLException {
        String sql = "INSERT INTO city_packages (player_uuid, items_blob, world) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerUuid);
            statement.setString(2, itemsBlob);
            statement.setString(3, world);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        }
    }

    /** Nur RECEIVE-Server: offene Pakete, älteste zuerst (FIFO). */
    public List<CityPackage> findUnclaimed() throws SQLException {
        String sql = "SELECT id, player_uuid, items_blob, world, created_at, claimed" +
                " FROM city_packages WHERE claimed = 0 ORDER BY created_at ASC, id ASC";
        List<CityPackage> packages = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Timestamp createdAt = result.getTimestamp("created_at");
                packages.add(new CityPackage(
                        result.getInt("id"),
                        UUID.fromString(result.getString("player_uuid")),
                        result.getString("items_blob"),
                        result.getString("world"),
                        createdAt == null ? null : createdAt.toInstant(),
                        result.getBoolean("claimed")
                ));
            }
        }
        return packages;
    }

    /** Nur RECEIVE-Server: Paket als abgeholt markieren. Liefert false, wenn schon abgeholt. */
    public boolean markClaimed(int id) throws SQLException {
        String sql = "UPDATE city_packages SET claimed = 1 WHERE id = ? AND claimed = 0";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        }
    }
}
