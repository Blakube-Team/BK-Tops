package com.blakube.bktops.plugin.storage.database.table;

import com.blakube.bktops.plugin.storage.database.connection.DatabaseConnection;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaCreator {

    private SchemaCreator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void createTopTable(@NotNull String topId) {
        String tableName = getTopTableName(topId);

        if (tableExists(tableName)) {
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "identifier VARCHAR(255) PRIMARY KEY, " +
                    "display_name VARCHAR(255) NOT NULL, " +
                    "top_value DOUBLE NOT NULL, " +
                    "last_updated BIGINT NOT NULL" +
                    ")";

            stmt.executeUpdate(sql);

            String indexSql = "CREATE INDEX IF NOT EXISTS idx_" +
                    sanitizeTableName(topId) + "_value ON " +
                    tableName + "(top_value DESC)";
            stmt.executeUpdate(indexSql);

            Bukkit.getLogger().info("[BK-Tops] Created table: " + tableName);

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[BK-Tops] Error creating table " + tableName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void createSnapshotTable(@NotNull String topId) {
        String tableName = getSnapshotTableName(topId);

        if (tableExists(tableName)) {
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "identifier VARCHAR(255) PRIMARY KEY, " +
                    "snapshot_value DOUBLE NOT NULL, " +
                    "snapshot_date BIGINT NOT NULL" +
                    ")";

            stmt.executeUpdate(sql);


            String indexSql = "CREATE INDEX IF NOT EXISTS idx_" +
                    sanitizeTableName(topId) + "_snapshot_date ON " +
                    tableName + "(snapshot_date DESC)";
            stmt.executeUpdate(indexSql);

            Bukkit.getLogger().info("[BK-Tops] Created snapshot table: " + tableName);

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[BK-Tops] Error creating snapshot table " + tableName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void createMetaTable(@NotNull String topId) {
        String tableName = getMetaTableName(topId);

        if (tableExists(tableName)) {
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id INT PRIMARY KEY, " +
                    "start_time BIGINT NOT NULL, " +
                    "next_reset_time BIGINT NOT NULL, " +
                    "last_reset_time BIGINT NULL" +
                    ")";

            stmt.executeUpdate(sql);
            Bukkit.getLogger().info("[BK-Tops] Created meta table: " + tableName);

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[BK-Tops] Error creating meta table " + tableName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean dropTopTable(@NotNull String topId) {
        String tableName = getTopTableName(topId);

        if (!tableExists(tableName)) {
            return false;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
            Bukkit.getLogger().info("[BK-Tops] Dropped table: " + tableName);
            return true;

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[BK-Tops] Error dropping table " + tableName + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean dropSnapshotTable(@NotNull String topId) {
        String tableName = getSnapshotTableName(topId);

        if (!tableExists(tableName)) {
            return false;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
            Bukkit.getLogger().info("[BK-Tops] Dropped snapshot table: " + tableName);
            return true;

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[BK-Tops] Error dropping snapshot table " + tableName + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void truncateTopTable(@NotNull String topId) {
        String tableName = getTopTableName(topId);

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("TRUNCATE TABLE " + tableName);

        } catch (SQLException e) {
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate("DELETE FROM " + tableName);

            } catch (SQLException ex) {
                Bukkit.getLogger().severe("[BK-Tops] Error truncating table " + tableName + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public static void truncateSnapshotTable(@NotNull String topId) {
        String tableName = getSnapshotTableName(topId);

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("TRUNCATE TABLE " + tableName);

        } catch (SQLException e) {
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate("DELETE FROM " + tableName);

            } catch (SQLException ex) {
                Bukkit.getLogger().severe("[BK-Tops] Error truncating snapshot table " + tableName + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public static boolean tableExists(@NotNull String tableName) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }

            try (ResultSet rs = meta.getTables(null, null, tableName.toLowerCase(), new String[]{"TABLE"})) {
                return rs.next();
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @NotNull
    public static String getTopTableName(@NotNull String topId) {
        return "top_" + sanitizeTableName(topId);
    }

    @NotNull
    public static String getSnapshotTableName(@NotNull String topId) {
        return "snapshot_" + sanitizeTableName(topId);
    }

    @NotNull
    public static String getMetaTableName(@NotNull String topId) {
        return "meta_" + sanitizeTableName(topId);
    }

    @NotNull
    private static String sanitizeTableName(@NotNull String name) {
        return name.toLowerCase()
                .replace("-", "_")
                .replace(" ", "_")
                .replace(".", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
}