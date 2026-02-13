package com.blakube.bktops.plugin.storage.database.dao;

import com.blakube.bktops.plugin.storage.database.connection.DatabaseConnection;
import com.blakube.bktops.plugin.storage.database.table.SchemaCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;

public final class TimedMetaDAO {

    private final String topId;
    private final String tableName;

    public TimedMetaDAO(@NotNull String topId) {
        this.topId = topId;
        this.tableName = SchemaCreator.getMetaTableName(topId);
        SchemaCreator.createMetaTable(topId);
    }

    public void save(long startTime, long nextResetTime, @Nullable Long lastResetTime) {
        boolean isMySql = "mysql".equalsIgnoreCase(DatabaseConnection.getDriver());
        String sql;
        if (isMySql) {
            sql = String.format(
                "INSERT INTO %s (id, start_time, next_reset_time, last_reset_time) VALUES (1, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE start_time = VALUES(start_time), next_reset_time = VALUES(next_reset_time), last_reset_time = VALUES(last_reset_time)",
                tableName
            );
        } else {
            sql = String.format(
                "MERGE INTO %s (id, start_time, next_reset_time, last_reset_time) VALUES (1, ?, ?, ?)",
                tableName
            );
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, nextResetTime);
            if (lastResetTime == null) {
                stmt.setNull(3, Types.BIGINT);
            } else {
                stmt.setLong(3, lastResetTime);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public Meta load() {
        String sql = String.format("SELECT start_time, next_reset_time, last_reset_time FROM %s WHERE id = 1", tableName);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long start = rs.getLong("start_time");
                    long next = rs.getLong("next_reset_time");
                    long last = rs.getLong("last_reset_time");
                    return new Meta(start, next, rs.wasNull() ? null : last);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static final class Meta {
        private final long startTime;
        private final long nextResetTime;
        private final Long lastResetTime;

        public Meta(long startTime, long nextResetTime, Long lastResetTime) {
            this.startTime = startTime;
            this.nextResetTime = nextResetTime;
            this.lastResetTime = lastResetTime;
        }

        public long getStartTime() { return startTime; }
        public long getNextResetTime() { return nextResetTime; }
        public Long getLastResetTime() { return lastResetTime; }
    }
}
