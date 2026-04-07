package nl.amk.blocklookup.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class SqliteDatabase {

    private final File file;
    private final String jdbcUrl;
    private final int busyTimeoutMs;

    public SqliteDatabase(File file, int busyTimeoutMs) {
        this.file = Objects.requireNonNull(file, "file");
        this.busyTimeoutMs = busyTimeoutMs;
        this.jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        applyPragmas(connection, busyTimeoutMs);
        return connection;
    }

    public void initialize() {
        try (Connection connection = openConnection()) {
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS block_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "time_ms INTEGER NOT NULL," +
                        "player_uuid TEXT," +
                        "player_name TEXT," +
                        "world_uuid TEXT NOT NULL," +
                        "x INTEGER NOT NULL," +
                        "y INTEGER NOT NULL," +
                        "z INTEGER NOT NULL," +
                        "action TEXT NOT NULL," +
                        "material_before TEXT NOT NULL," +
                        "data_before TEXT NOT NULL," +
                        "material_after TEXT NOT NULL," +
                        "data_after TEXT NOT NULL" +
                        ")");

                st.executeUpdate("CREATE TABLE IF NOT EXISTS chat_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "time_ms INTEGER NOT NULL," +
                        "player_uuid TEXT," +
                        "player_name TEXT," +
                        "message TEXT NOT NULL" +
                        ")");

                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_block_events_time ON block_events(time_ms)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_block_events_player_time ON block_events(player_uuid, time_ms)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_block_events_world_xyz_time ON block_events(world_uuid, x, y, z, time_ms)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chat_events_time ON chat_events(time_ms)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chat_events_player_time ON chat_events(player_uuid, time_ms)");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize SQLite database at " + file.getAbsolutePath(), e);
        }
    }

    public void close() {
    }

    private static void applyPragmas(Connection connection, int busyTimeoutMs) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA temp_store=MEMORY");
            st.execute("PRAGMA busy_timeout=" + busyTimeoutMs);
        }
    }
}

