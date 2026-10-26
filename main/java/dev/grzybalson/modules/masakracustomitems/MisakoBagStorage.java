package dev.grzybalson.modules.masakracustomitems;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;

/** One durable, authoritative inventory per bag ID, including copied physical bags. */
final class MisakoBagStorage implements AutoCloseable {
    private final Connection connection;

    MisakoBagStorage(Path file) throws SQLException, IOException, ClassNotFoundException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("CREATE TABLE IF NOT EXISTS bags (id TEXT PRIMARY KEY, contents TEXT NOT NULL)");
        } catch (SQLException failure) {
            connection.close();
            throw failure;
        }
    }

    String load(UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT contents FROM bags WHERE id=?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    /** A failed write never authorizes removing loot or handing an item to a player. */
    void save(UUID id, String contents) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO bags(id,contents) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET contents=excluded.contents")) {
            statement.setString(1, id.toString());
            statement.setString(2, contents);
            statement.executeUpdate();
        }
    }

    @Override public void close() throws SQLException { connection.close(); }
}
