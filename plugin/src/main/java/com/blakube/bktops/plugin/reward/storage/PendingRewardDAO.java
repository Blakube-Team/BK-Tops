package com.blakube.bktops.plugin.reward.storage;

import com.blakube.bktops.plugin.reward.PendingReward;
import com.blakube.bktops.plugin.reward.RewardActionType;
import com.blakube.bktops.plugin.storage.database.connection.DatabaseConnection;
import com.blakube.bktops.plugin.storage.database.table.SchemaCreator;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class PendingRewardDAO {

    private final String tableName;

    public PendingRewardDAO() {
        this.tableName = SchemaCreator.getPendingRewardsTableName();
        initialize();
    }

    public void initialize() {
        SchemaCreator.createPendingRewardsTable();
    }

    public void saveBatch(@NotNull Collection<PendingReward> rewards) {
        if (rewards.isEmpty()) return;

        String sql = "INSERT INTO " + tableName + " " +
                "(id, batch_id, top_id, player_uuid, player_name, position, score, action_type, payload, amount, created_at, delivered_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (PendingReward reward : rewards) {
                bindInsert(stmt, reward);
                stmt.addBatch();
            }
            stmt.executeBatch();

        } catch (SQLException e) {
            throw new IllegalStateException("Could not save pending rewards", e);
        }
    }

    @NotNull
    public List<PendingReward> loadUndelivered() {
        String sql = "SELECT * FROM " + tableName + " WHERE delivered_at IS NULL";
        List<PendingReward> rewards = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                rewards.add(read(rs));
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Could not load pending rewards", e);
        }

        return rewards;
    }

    public void markDelivered(@NotNull Collection<String> ids, long deliveredAt) {
        if (ids.isEmpty()) return;

        String sql = "UPDATE " + tableName + " SET delivered_at = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (String id : ids) {
                stmt.setLong(1, deliveredAt);
                stmt.setString(2, id);
                stmt.addBatch();
            }
            stmt.executeBatch();

        } catch (SQLException e) {
            throw new IllegalStateException("Could not mark rewards as delivered", e);
        }
    }

    public void purgeDeliveredBefore(long timestamp) {
        String sql = "DELETE FROM " + tableName + " WHERE delivered_at IS NOT NULL AND delivered_at < ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, timestamp);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("Could not purge delivered rewards", e);
        }
    }

    private void bindInsert(@NotNull PreparedStatement stmt, @NotNull PendingReward reward) throws SQLException {
        stmt.setString(1, reward.id());
        stmt.setString(2, reward.batchId());
        stmt.setString(3, reward.topId());
        stmt.setString(4, reward.playerUuid().toString());
        if (reward.playerName() == null) {
            stmt.setNull(5, Types.VARCHAR);
        } else {
            stmt.setString(5, reward.playerName());
        }
        stmt.setInt(6, reward.position());
        stmt.setDouble(7, reward.score());
        stmt.setString(8, reward.actionType().name());
        stmt.setString(9, reward.payload());
        stmt.setInt(10, reward.amount());
        stmt.setLong(11, reward.createdAt());
    }

    @NotNull
    private PendingReward read(@NotNull ResultSet rs) throws SQLException {
        return new PendingReward(
                rs.getString("id"),
                rs.getString("batch_id"),
                rs.getString("top_id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getInt("position"),
                rs.getDouble("score"),
                RewardActionType.valueOf(rs.getString("action_type")),
                rs.getString("payload"),
                rs.getInt("amount"),
                rs.getLong("created_at")
        );
    }
}
