package nl.amk.blocklookup.db;

import nl.amk.blocklookup.db.write.BlockEventWrite;
import nl.amk.blocklookup.db.write.ChatEventWrite;
import nl.amk.blocklookup.db.write.DbWrite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseWriter {

    private static final int DEFAULT_QUEUE_CAPACITY = 250_000;

    private final Logger logger;
    private final SqliteDatabase database;
    private final int batchSize;
    private final int flushIntervalTicks;
    private final LinkedBlockingQueue<DbWrite> queue;
    private final AtomicBoolean running;
    private final AtomicLong droppedWrites;
    private volatile long nextDropLogMs;
    private Thread thread;

    public DatabaseWriter(Logger logger, SqliteDatabase database, int batchSize, int flushIntervalTicks) {
        this.logger = logger;
        this.database = database;
        this.batchSize = batchSize;
        this.flushIntervalTicks = flushIntervalTicks;
        this.queue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
        this.running = new AtomicBoolean(false);
        this.droppedWrites = new AtomicLong();
        this.nextDropLogMs = 0L;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        thread = new Thread(this::runLoop, "BlockLookup-DB");
        thread.setDaemon(true);
        thread.start();
    }

    public void enqueue(BlockEventWrite write) {
        if (!running.get()) return;
        if (!queue.offer(write)) {
            onDrop();
        }
    }

    public void enqueue(ChatEventWrite write) {
        if (!running.get()) return;
        if (!queue.offer(write)) {
            onDrop();
        }
    }

    public void shutdownAndFlush(Duration timeout) {
        if (!running.compareAndSet(true, false)) return;
        Thread t = thread;
        if (t != null) {
            t.interrupt();
            try {
                t.join(timeout.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runLoop() {
        long pollMs = Math.max(1L, flushIntervalTicks) * 50L;
        List<DbWrite> batch = new ArrayList<>(batchSize);

        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement insertBlock = connection.prepareStatement(
                    "INSERT INTO block_events(time_ms, player_uuid, player_name, world_uuid, x, y, z, action, material_before, data_before, material_after, data_after) " +
                            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            );
                 PreparedStatement insertChat = connection.prepareStatement(
                         "INSERT INTO chat_events(time_ms, player_uuid, player_name, message) VALUES(?, ?, ?, ?)"
                 )) {

                while (running.get() || !queue.isEmpty()) {
                    DbWrite first = null;
                    try {
                        first = queue.poll(pollMs, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                    }

                    if (first == null) {
                        flush(connection, insertBlock, insertChat, batch);
                        continue;
                    }

                    batch.add(first);
                    queue.drainTo(batch, batchSize - 1);

                    flush(connection, insertBlock, insertChat, batch);
                }

                flush(connection, insertBlock, insertChat, batch);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database writer failed", e);
        }
    }

    private void onDrop() {
        long dropped = droppedWrites.incrementAndGet();
        long now = System.currentTimeMillis();
        long next = nextDropLogMs;
        if (now < next) return;
        nextDropLogMs = now + 60_000L;
        logger.warning("Database queue is full, dropping log entries. dropped=" + dropped);
    }

    private void flush(Connection connection, PreparedStatement insertBlock, PreparedStatement insertChat, List<DbWrite> batch) throws SQLException {
        if (batch.isEmpty()) return;

        for (DbWrite write : batch) {
            if (write instanceof BlockEventWrite b) {
                bindBlock(insertBlock, b);
                insertBlock.addBatch();
            } else if (write instanceof ChatEventWrite c) {
                bindChat(insertChat, c);
                insertChat.addBatch();
            }
        }

        try {
            insertBlock.executeBatch();
            insertChat.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.log(Level.WARNING, "Database batch failed, rolled back", e);
        } finally {
            batch.clear();
            insertBlock.clearBatch();
            insertChat.clearBatch();
        }
    }

    private static void bindBlock(PreparedStatement ps, BlockEventWrite b) throws SQLException {
        ps.setLong(1, b.timeMs());
        bindUuid(ps, 2, b.playerUuid());
        ps.setString(3, b.playerName());
        ps.setString(4, b.worldUuid().toString());
        ps.setInt(5, b.x());
        ps.setInt(6, b.y());
        ps.setInt(7, b.z());
        ps.setString(8, b.action());
        ps.setString(9, b.before().materialKey());
        ps.setString(10, safeString(b.before().blockData()));
        ps.setString(11, b.after().materialKey());
        ps.setString(12, safeString(b.after().blockData()));
    }

    private static void bindChat(PreparedStatement ps, ChatEventWrite c) throws SQLException {
        ps.setLong(1, c.timeMs());
        bindUuid(ps, 2, c.playerUuid());
        ps.setString(3, c.playerName());
        ps.setString(4, c.message());
    }

    private static void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (uuid == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, uuid.toString());
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
