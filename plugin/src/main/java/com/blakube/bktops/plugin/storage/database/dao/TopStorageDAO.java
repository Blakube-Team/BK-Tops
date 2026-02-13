package com.blakube.bktops.plugin.storage.database.dao;

import com.blakube.bktops.api.top.TopEntry;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseConnection;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import com.blakube.bktops.plugin.storage.database.table.SchemaCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TopStorageDAO<K> {

    private final String topId;
    private final String tableName;
    private final IdentifierSerializer<K> serializer;

    private volatile int cachedSize = -1;
    private volatile long lastSizeUpdate = 0;
    private static final long SIZE_CACHE_MS = 10000;

    public TopStorageDAO(@NotNull String topId, @NotNull IdentifierSerializer<K> serializer) {
        this.topId = topId;
        this.tableName = SchemaCreator.getTopTableName(topId);
        this.serializer = serializer;

        SchemaCreator.createTopTable(topId);
    }

    @NotNull
    public List<TopEntry<K>> loadAll() {
        List<TopEntry<K>> entries = new ArrayList<>();

        String sql = String.format(
                "SELECT identifier, display_name, top_value, last_updated FROM %s ORDER BY top_value DESC",
                tableName
        );

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int position = 1;
            while (rs.next()) {
                K identifier = serializer.deserialize(rs.getString("identifier"));
                String displayName = rs.getString("display_name");
                double value = rs.getDouble("top_value");
                long lastUpdated = rs.getLong("last_updated");

                entries.add(new TopEntry<>(identifier, displayName, value, position, lastUpdated));
                position++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return entries;
    }

    @NotNull
    public CompletableFuture<List<TopEntry<K>>> loadAllAsync() {
        return CompletableFuture.supplyAsync(this::loadAll, DatabaseExecutors.DB_EXECUTOR);
    }

    public boolean save(@NotNull K identifier, @NotNull String displayName, double value) {
        boolean isMySql = "mysql".equalsIgnoreCase(DatabaseConnection.getDriver());
        String sql;

        if (isMySql) {
            sql = String.format(
                    "INSERT INTO %s (identifier, display_name, top_value, last_updated) VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), top_value = VALUES(top_value), last_updated = VALUES(last_updated)",
                    tableName
            );
        } else {
            sql = String.format(
                    "MERGE INTO %s (identifier, display_name, top_value, last_updated) VALUES (?, ?, ?, ?)",
                    tableName
            );
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serializer.serialize(identifier));
            stmt.setString(2, displayName);
            stmt.setDouble(3, value);
            stmt.setLong(4, System.currentTimeMillis());

            stmt.executeUpdate();

            cachedSize = -1;

            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public CompletableFuture<Boolean> saveAsync(@NotNull K identifier, @NotNull String displayName, double value) {
        return CompletableFuture.supplyAsync(() -> save(identifier, displayName, value), DatabaseExecutors.DB_EXECUTOR);
    }

    public void saveBatch(@NotNull List<BatchEntry<K>> entries) {
        if (entries.isEmpty()) {
            return;
        }

        boolean isMySql = "mysql".equalsIgnoreCase(DatabaseConnection.getDriver());
        String sql;

        if (isMySql) {
            sql = String.format(
                    "INSERT INTO %s (identifier, display_name, top_value, last_updated) VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), top_value = VALUES(top_value), last_updated = VALUES(last_updated)",
                    tableName
            );
        } else {
            sql = String.format(
                    "MERGE INTO %s (identifier, display_name, top_value, last_updated) VALUES (?, ?, ?, ?)",
                    tableName
            );
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            long now = System.currentTimeMillis();

            for (BatchEntry<K> entry : entries) {
                stmt.setString(1, serializer.serialize(entry.identifier));
                stmt.setString(2, entry.displayName);
                stmt.setDouble(3, entry.value);
                stmt.setLong(4, now);
                stmt.addBatch();
            }

            stmt.executeBatch();

            cachedSize = -1;

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> saveBatchAsync(@NotNull List<BatchEntry<K>> entries) {
        return CompletableFuture.runAsync(() -> saveBatch(entries), DatabaseExecutors.DB_EXECUTOR);
    }

    public boolean delete(@NotNull K identifier) {
        String sql = String.format("DELETE FROM %s WHERE identifier = ?", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serializer.serialize(identifier));
            boolean deleted = stmt.executeUpdate() > 0;

            if (deleted) {
                cachedSize = -1;
            }

            return deleted;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public CompletableFuture<Boolean> deleteAsync(@NotNull K identifier) {
        return CompletableFuture.supplyAsync(() -> delete(identifier), DatabaseExecutors.DB_EXECUTOR);
    }

    @NotNull
    public Optional<TopEntry<K>> get(@NotNull K identifier) {
        String sql = String.format(
                "SELECT display_name, top_value, last_updated FROM %s WHERE identifier = ?",
                tableName
        );

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serializer.serialize(identifier));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String displayName = rs.getString("display_name");
                    double value = rs.getDouble("top_value");
                    long lastUpdated = rs.getLong("last_updated");
                    int position = getPosition(identifier);

                    return Optional.of(new TopEntry<>(identifier, displayName, value, position, lastUpdated));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public CompletableFuture<Optional<TopEntry<K>>> getAsync(@NotNull K identifier) {
        return CompletableFuture.supplyAsync(() -> get(identifier), DatabaseExecutors.DB_EXECUTOR);
    }

    public int getPosition(@NotNull K identifier) {
        String sql = String.format(
                "SELECT COUNT(*) + 1 as position FROM %s WHERE top_value > (SELECT top_value FROM %s WHERE identifier = ?)",
                tableName, tableName
        );

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serializer.serialize(identifier));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("position");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    public CompletableFuture<Integer> getPositionAsync(@NotNull K identifier) {
        return CompletableFuture.supplyAsync(() -> getPosition(identifier), DatabaseExecutors.DB_EXECUTOR);
    }

    @Nullable
    public Double getMinValue() {
        String sql = String.format("SELECT MIN(top_value) as min_value FROM %s", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                double value = rs.getDouble("min_value");
                if (!rs.wasNull()) {
                    return value;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public CompletableFuture<Double> getMinValueAsync() {
        return CompletableFuture.supplyAsync(this::getMinValue, DatabaseExecutors.DB_EXECUTOR);
    }

    @Nullable
    public Double getMaxValue() {
        String sql = String.format("SELECT MAX(top_value) as max_value FROM %s", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                double value = rs.getDouble("max_value");
                if (!rs.wasNull()) {
                    return value;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public CompletableFuture<Double> getMaxValueAsync() {
        return CompletableFuture.supplyAsync(this::getMaxValue, DatabaseExecutors.DB_EXECUTOR);
    }

    public int getSize() {
        long now = System.currentTimeMillis();

        if (cachedSize >= 0 && (now - lastSizeUpdate) < SIZE_CACHE_MS) {
            return cachedSize;
        }

        String sql = String.format("SELECT COUNT(*) as count FROM %s", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                cachedSize = rs.getInt("count");
                lastSizeUpdate = now;
                return cachedSize;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public CompletableFuture<Integer> getSizeAsync() {
        return CompletableFuture.supplyAsync(this::getSize, DatabaseExecutors.DB_EXECUTOR);
    }

    public boolean removeLowest() {
        String sql = String.format(
                "DELETE FROM %s WHERE identifier = (SELECT identifier FROM %s ORDER BY top_value ASC LIMIT 1)",
                tableName, tableName
        );

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            boolean removed = stmt.executeUpdate(sql) > 0;

            if (removed) {
                cachedSize = -1;
            }

            return removed;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public CompletableFuture<Boolean> removeLowestAsync() {
        return CompletableFuture.supplyAsync(this::removeLowest, DatabaseExecutors.DB_EXECUTOR);
    }

    public void clear() {
        SchemaCreator.truncateTopTable(topId);

        cachedSize = -1;
    }

    public CompletableFuture<Void> clearAsync() {
        return CompletableFuture.runAsync(() -> {
            SchemaCreator.truncateTopTable(topId);
            cachedSize = -1;
        }, DatabaseExecutors.DB_EXECUTOR);
    }

    public boolean exists(@NotNull K identifier) {
        String sql = String.format("SELECT 1 FROM %s WHERE identifier = ?", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serializer.serialize(identifier));

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public CompletableFuture<Boolean> existsAsync(@NotNull K identifier) {
        return CompletableFuture.supplyAsync(() -> exists(identifier), DatabaseExecutors.DB_EXECUTOR);
    }

    public static class BatchEntry<K> {
        public final K identifier;
        public final String displayName;
        public final double value;

        public BatchEntry(K identifier, String displayName, double value) {
            this.identifier = identifier;
            this.displayName = displayName;
            this.value = value;
        }
    }

    public interface IdentifierSerializer<K> {
        @NotNull
        String serialize(@NotNull K identifier);

        @NotNull
        K deserialize(@NotNull String serialized);
    }
}