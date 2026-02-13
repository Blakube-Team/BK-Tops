package com.blakube.bktops.plugin.storage.database.dao;

import com.blakube.bktops.plugin.storage.database.connection.DatabaseConnection;
import com.blakube.bktops.plugin.storage.database.table.SchemaCreator;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseExecutors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SnapshotDAO<K> {

    private final String topId;
    private final String tableName;
    private final TopStorageDAO.IdentifierSerializer<K> serializer;

    public SnapshotDAO(@NotNull String topId, @NotNull TopStorageDAO.IdentifierSerializer<K> serializer) {
        this.topId = topId;
        this.tableName = SchemaCreator.getSnapshotTableName(topId);
        this.serializer = serializer;

        SchemaCreator.createSnapshotTable(topId);
    }

    @Nullable
    public Double getSnapshot(@NotNull K identifier) {
        String sql = String.format("SELECT snapshot_value FROM %s WHERE identifier = ?", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serializer.serialize(identifier));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("snapshot_value");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    @NotNull
    public CompletableFuture<Double> getSnapshotAsync(@NotNull K identifier) {
        return CompletableFuture.supplyAsync(() -> getSnapshot(identifier), DatabaseExecutors.DB_EXECUTOR);
    }

    public void setSnapshot(@NotNull K identifier, double snapshotValue) {

        boolean isMySql = "mysql".equalsIgnoreCase(DatabaseConnection.getDriver());
        String sql;
        if (isMySql) {
            sql = String.format("INSERT IGNORE INTO %s (identifier, snapshot_value, snapshot_date) VALUES (?, ?, ?)", tableName);
        } else {
            sql = String.format("INSERT INTO %s (identifier, snapshot_value, snapshot_date) VALUES (?, ?, ?)", tableName);
        }

        String serializedId = serializer.serialize(identifier);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serializedId);
            stmt.setDouble(2, snapshotValue);
            stmt.setLong(3, System.currentTimeMillis());

            stmt.executeUpdate();

        } catch (SQLException ignored) {}
    }

    @NotNull
    public CompletableFuture<Void> setSnapshotAsync(@NotNull K identifier, double snapshotValue) {
        return CompletableFuture.runAsync(() -> setSnapshot(identifier, snapshotValue), DatabaseExecutors.DB_EXECUTOR);
    }

    public void saveBatch(@NotNull Map<K, Double> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }


        boolean isMySql = "mysql".equalsIgnoreCase(DatabaseConnection.getDriver());
        String sql;

        if (isMySql) {
            sql = String.format(
                    "INSERT INTO %s (identifier, snapshot_value, snapshot_date) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE snapshot_value = VALUES(snapshot_value), snapshot_date = VALUES(snapshot_date)",
                    tableName
            );
        } else {
            sql = String.format(
                    "MERGE INTO %s (identifier, snapshot_value, snapshot_date) VALUES (?, ?, ?)",
                    tableName
            );
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            long now = System.currentTimeMillis();

            for (Map.Entry<K, Double> entry : snapshots.entrySet()) {
                String serializedId = serializer.serialize(entry.getKey());
                double value = entry.getValue();


                stmt.setString(1, serializedId);
                stmt.setDouble(2, value);
                stmt.setLong(3, now);
                stmt.addBatch();
            }

            stmt.executeBatch();

        } catch (SQLException e) {
            System.err.println("[SnapshotDAO][" + topId + "] Error saving batch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> saveBatchAsync(@NotNull Map<K, Double> snapshots) {
        return CompletableFuture.runAsync(() -> saveBatch(snapshots), DatabaseExecutors.DB_EXECUTOR);
    }

    @NotNull
    public Map<K, Double> getAllSnapshots() {
        Map<K, Double> snapshots = new HashMap<>();

        String sql = String.format("SELECT identifier, snapshot_value FROM %s", tableName);


        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int count = 0;
            while (rs.next()) {
                String serializedId = rs.getString("identifier");
                double value = rs.getDouble("snapshot_value");

                K identifier = serializer.deserialize(serializedId);

                snapshots.put(identifier, value);
                count++;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return snapshots;
    }

    @NotNull
    public CompletableFuture<Map<K, Double>> getAllSnapshotsAsync() {
        return CompletableFuture.supplyAsync(this::getAllSnapshots, DatabaseExecutors.DB_EXECUTOR);
    }

    public boolean deleteSnapshot(@NotNull K identifier) {
        String sql = String.format("DELETE FROM %s WHERE identifier = ?", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serializer.serialize(identifier));
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @NotNull
    public CompletableFuture<Boolean> deleteSnapshotAsync(@NotNull K identifier) {
        return CompletableFuture.supplyAsync(() -> deleteSnapshot(identifier), DatabaseExecutors.DB_EXECUTOR);
    }

    public void clearSnapshots() {
        SchemaCreator.truncateSnapshotTable(topId);
    }

    @NotNull
    public CompletableFuture<Void> clearSnapshotsAsync() {
        return CompletableFuture.runAsync(() -> SchemaCreator.truncateSnapshotTable(topId), DatabaseExecutors.DB_EXECUTOR);
    }

    public void initializeIfAbsent(@NotNull K identifier, double currentValue) {
        Double existing = getSnapshot(identifier);
        if (existing == null) {
            setSnapshot(identifier, currentValue);
        }
    }

    public CompletableFuture<Void> initializeIfAbsentAsync(@NotNull K identifier, double currentValue) {
        return CompletableFuture.runAsync(() -> initializeIfAbsent(identifier, currentValue), DatabaseExecutors.DB_EXECUTOR);
    }

    public boolean hasSnapshot(@NotNull K identifier) {
        return getSnapshot(identifier) != null;
    }

    public CompletableFuture<Boolean> hasSnapshotAsync(@NotNull K identifier) {
        return CompletableFuture.supplyAsync(() -> hasSnapshot(identifier), DatabaseExecutors.DB_EXECUTOR);
    }

    public int count() {
        String sql = String.format("SELECT COUNT(*) as count FROM %s", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public CompletableFuture<Integer> countAsync() {
        return CompletableFuture.supplyAsync(this::count, DatabaseExecutors.DB_EXECUTOR);
    }
}