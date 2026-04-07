package nl.amk.blocklookup.db;

import nl.amk.blocklookup.db.model.BlockEventRecord;
import nl.amk.blocklookup.db.model.BlockSnapshot;
import nl.amk.blocklookup.db.model.ChatEventRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EventQueryService {

    private final SqliteDatabase database;

    public EventQueryService(SqliteDatabase database) {
        this.database = database;
    }

    public List<BlockEventRecord> findNearbyBlockEvents(UUID worldUuid, int centerX, int centerY, int centerZ, int radius, long sinceMs, PlayerFilter filter, int limit) {
        if (worldUuid == null) return List.of();
        int effectiveLimit = Math.max(1, Math.min(limit, 200));
        int r = Math.max(0, Math.min(radius, 500));
        int minX = centerX - r;
        int maxX = centerX + r;
        int minY = centerY - r;
        int maxY = centerY + r;
        int minZ = centerZ - r;
        int maxZ = centerZ + r;

        List<Object> params = new ArrayList<>(10);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, time_ms, player_uuid, player_name, world_uuid, x, y, z, action, material_before, data_before, material_after, data_after ");
        sql.append("FROM block_events WHERE world_uuid=? AND time_ms>=? ");
        params.add(worldUuid.toString());
        params.add(sinceMs);
        sql.append("AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? ");
        params.add(minX);
        params.add(maxX);
        params.add(minY);
        params.add(maxY);
        params.add(minZ);
        params.add(maxZ);

        appendPlayerFilter(sql, params, filter);
        sql.append("ORDER BY id DESC LIMIT ?");
        params.add(effectiveLimit);

        List<BlockEventRecord> records = new ArrayList<>(effectiveLimit);
        try (Connection connection = database.openConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(readBlockRecord(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed at " + Instant.ofEpochMilli(sinceMs), e);
        }

        if (r == 0) return records;
        int r2 = r * r;
        List<BlockEventRecord> filtered = new ArrayList<>(records.size());
        for (BlockEventRecord record : records) {
            int dx = record.x() - centerX;
            int dz = record.z() - centerZ;
            if (dx * dx + dz * dz <= r2) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    public List<BlockEventRecord> findRollbackCandidates(UUID worldUuid, int centerX, int centerY, int centerZ, int radius, long sinceMs, PlayerFilter filter, int limit) {
        if (worldUuid == null) return List.of();
        int effectiveLimit = Math.max(1, limit);
        int r = Math.max(0, Math.min(radius, 500));
        int minX = centerX - r;
        int maxX = centerX + r;
        int minY = centerY - r;
        int maxY = centerY + r;
        int minZ = centerZ - r;
        int maxZ = centerZ + r;

        List<Object> params = new ArrayList<>(10);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, time_ms, player_uuid, player_name, world_uuid, x, y, z, action, material_before, data_before, material_after, data_after ");
        sql.append("FROM block_events WHERE world_uuid=? AND time_ms>=? ");
        params.add(worldUuid.toString());
        params.add(sinceMs);
        sql.append("AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? ");
        params.add(minX);
        params.add(maxX);
        params.add(minY);
        params.add(maxY);
        params.add(minZ);
        params.add(maxZ);

        appendPlayerFilter(sql, params, filter);
        sql.append("ORDER BY id DESC LIMIT ?");
        params.add(effectiveLimit);

        List<BlockEventRecord> records = new ArrayList<>(Math.min(effectiveLimit, 2000));
        try (Connection connection = database.openConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(readBlockRecord(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Rollback query failed at " + Instant.ofEpochMilli(sinceMs), e);
        }

        if (r == 0) return records;
        int r2 = r * r;
        List<BlockEventRecord> filtered = new ArrayList<>(records.size());
        for (BlockEventRecord record : records) {
            int dx = record.x() - centerX;
            int dz = record.z() - centerZ;
            if (dx * dx + dz * dz <= r2) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    public List<ChatEventRecord> findChatEvents(long sinceMs, PlayerFilter filter, int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 500));
        List<Object> params = new ArrayList<>(4);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, time_ms, player_uuid, player_name, message FROM chat_events WHERE time_ms>=? ");
        params.add(sinceMs);
        appendPlayerFilter(sql, params, filter);
        sql.append("ORDER BY id DESC LIMIT ?");
        params.add(effectiveLimit);

        List<ChatEventRecord> records = new ArrayList<>(effectiveLimit);
        try (Connection connection = database.openConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(readChatRecord(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Chat query failed at " + Instant.ofEpochMilli(sinceMs), e);
        }
        return records;
    }

    private static void appendPlayerFilter(StringBuilder sql, List<Object> params, PlayerFilter filter) {
        if (filter == null || filter.isAny()) return;
        if (filter.uuid() != null) {
            sql.append("AND player_uuid=? ");
            params.add(filter.uuid().toString());
            return;
        }
        if (filter.name() != null && !filter.name().isBlank()) {
            sql.append("AND lower(player_name)=lower(?) ");
            params.add(filter.name());
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            int idx = i + 1;
            if (value instanceof Integer v) ps.setInt(idx, v);
            else if (value instanceof Long v) ps.setLong(idx, v);
            else ps.setString(idx, String.valueOf(value));
        }
    }

    private static BlockEventRecord readBlockRecord(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long timeMs = rs.getLong("time_ms");
        UUID playerUuid = parseUuid(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        UUID worldUuid = UUID.fromString(rs.getString("world_uuid"));
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");
        String action = rs.getString("action");
        BlockSnapshot before = new BlockSnapshot(rs.getString("material_before"), rs.getString("data_before"));
        BlockSnapshot after = new BlockSnapshot(rs.getString("material_after"), rs.getString("data_after"));
        return new BlockEventRecord(id, timeMs, playerUuid, playerName, worldUuid, x, y, z, action, before, after);
    }

    private static ChatEventRecord readChatRecord(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long timeMs = rs.getLong("time_ms");
        UUID playerUuid = parseUuid(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        String message = rs.getString("message");
        return new ChatEventRecord(id, timeMs, playerUuid, playerName, message);
    }

    private static UUID parseUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
