package com.blakube.bktops.plugin.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class H2SqlTest {

    private Connection conn;
    private static final String TABLE = "top_money";

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        conn = ds.getConnection();

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE " + TABLE + " (" +
                "identifier VARCHAR(255) PRIMARY KEY, " +
                "display_name VARCHAR(255) NOT NULL, " +
                "top_value DOUBLE NOT NULL, " +
                "last_updated BIGINT NOT NULL" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE INDEX idx_top_money_value ON " + TABLE + "(top_value DESC)"
            );
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    private String upsertSql() {
        return "INSERT INTO " + TABLE + " (identifier, display_name, top_value, last_updated) VALUES (?, ?, ?, ?) " +
               "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), " +
               "top_value = VALUES(top_value), last_updated = VALUES(last_updated)";
    }

    private String trimSql() {
        return "DELETE FROM " + TABLE + " WHERE identifier NOT IN " +
               "(SELECT identifier FROM (SELECT identifier FROM " + TABLE + " ORDER BY top_value DESC LIMIT ?) AS keep_list)";
    }

    private void upsert(String id, String name, double value) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(upsertSql())) {
            stmt.setString(1, id);
            stmt.setString(2, name);
            stmt.setDouble(3, value);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    private List<String> loadTopIds() throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT identifier FROM " + TABLE + " ORDER BY top_value DESC")) {
            while (rs.next()) result.add(rs.getString("identifier"));
        }
        return result;
    }

    private int count() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void upsert_insertsNewRow() throws SQLException {
        upsert("uuid-1", "Player1", 1000.0);
        assertEquals(1, count());
    }

    @Test
    void upsert_updatesExistingRow() throws SQLException {
        upsert("uuid-1", "Player1", 1000.0);
        upsert("uuid-1", "Player1", 9999.0);

        assertEquals(1, count());

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT top_value FROM " + TABLE + " WHERE identifier = ?")) {
            stmt.setString(1, "uuid-1");
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(9999.0, rs.getDouble("top_value"), 0.001);
            }
        }
    }

    @Test
    void upsert_insertsLargeBalance() throws SQLException {
        upsert("uuid-1", "Diego", 113_000_000.0);

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT top_value FROM " + TABLE + " WHERE identifier = ?")) {
            stmt.setString(1, "uuid-1");
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(113_000_000.0, rs.getDouble("top_value"), 0.001);
            }
        }
    }

    @Test
    void trim_removesLowestBeyondLimit() throws SQLException {
        upsert("uuid-1", "P1", 500.0);
        upsert("uuid-2", "P2", 900.0);
        upsert("uuid-3", "P3", 100.0);
        upsert("uuid-4", "P4", 700.0);

        try (PreparedStatement stmt = conn.prepareStatement(trimSql())) {
            stmt.setInt(1, 3);
            stmt.executeUpdate();
        }

        assertEquals(3, count());
        List<String> remaining = loadTopIds();
        assertTrue(remaining.contains("uuid-2"));
        assertTrue(remaining.contains("uuid-4"));
        assertTrue(remaining.contains("uuid-1"));
        assertFalse(remaining.contains("uuid-3"));
    }

    @Test
    void trim_noopWhenUnderLimit() throws SQLException {
        upsert("uuid-1", "P1", 500.0);
        upsert("uuid-2", "P2", 900.0);

        try (PreparedStatement stmt = conn.prepareStatement(trimSql())) {
            stmt.setInt(1, 5);
            stmt.executeUpdate();
        }

        assertEquals(2, count());
    }

    @Test
    void upsertAndTrim_fullBatchFlow() throws SQLException {
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement stmt = conn.prepareStatement(upsertSql())) {
                long now = System.currentTimeMillis();
                for (int i = 1; i <= 20; i++) {
                    stmt.setString(1, "uuid-" + i);
                    stmt.setString(2, "Player" + i);
                    stmt.setDouble(3, i * 1000.0);
                    stmt.setLong(4, now);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            try (PreparedStatement stmt = conn.prepareStatement(trimSql())) {
                stmt.setInt(1, 15);
                stmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }

        assertEquals(15, count());
        List<String> top = loadTopIds();
        assertEquals("uuid-20", top.get(0));
        assertEquals("uuid-6", top.get(14));
    }

    @Test
    void load_returnsOrderedByValueDesc() throws SQLException {
        upsert("uuid-a", "PA", 300.0);
        upsert("uuid-b", "PB", 900.0);
        upsert("uuid-c", "PC", 50.0);

        List<String> ids = loadTopIds();
        assertEquals(List.of("uuid-b", "uuid-a", "uuid-c"), ids);
    }
}
