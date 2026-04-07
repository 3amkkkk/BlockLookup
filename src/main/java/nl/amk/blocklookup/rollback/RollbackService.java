package nl.amk.blocklookup.rollback;

import nl.amk.blocklookup.config.Settings;
import nl.amk.blocklookup.db.EventQueryService;
import nl.amk.blocklookup.db.PlayerFilter;
import nl.amk.blocklookup.db.model.BlockEventRecord;
import nl.amk.blocklookup.db.model.BlockSnapshot;
import nl.amk.blocklookup.util.GradientText;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;

public final class RollbackService {

    private final Plugin plugin;
    private final EventQueryService queries;
    private final Settings settings;

    public RollbackService(Plugin plugin, EventQueryService queries, Settings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void execute(Player sender, RollbackOperation operation, PlayerFilter filter, Duration duration, int radius, int limit) {
        long sinceMs = System.currentTimeMillis() - Math.max(0L, duration.toMillis());
        UUID worldUuid = sender.getWorld().getUID();
        int x = sender.getLocation().getBlockX();
        int y = sender.getLocation().getBlockY();
        int z = sender.getLocation().getBlockZ();

        int cappedLimit = Math.max(1, Math.min(limit, settings.maxActionsPerCommand()));
        int cappedRadius = Math.max(0, Math.min(radius, 500));

        sender.sendMessage(GradientText.prefix() + GradientText.darkGray() + "Searching logs..." + GradientText.reset());

        supplyAsync(() -> queries.findRollbackCandidates(worldUuid, x, y, z, cappedRadius, sinceMs, filter, cappedLimit))
                .thenAccept(records -> Bukkit.getScheduler().runTask(plugin, () -> apply(sender, operation, records)));
    }

    private void apply(Player sender, RollbackOperation operation, List<BlockEventRecord> records) {
        if (records.isEmpty()) {
            sender.sendMessage(GradientText.prefix() + GradientText.red() + "No matching block events found." + GradientText.reset());
            return;
        }

        int perTick = settings.applyPerTick();
        Deque<BlockEventRecord> queue = new ArrayDeque<>(records);
        AtomicInteger changed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger skippedUnloaded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (processed < perTick && !queue.isEmpty()) {
                    BlockEventRecord record = queue.pollFirst();
                    processed++;
                    if (record == null) break;

                    World world = Bukkit.getWorld(record.worldUuid());
                    if (world == null) {
                        failed.incrementAndGet();
                        continue;
                    }

                    int cx = record.x() >> 4;
                    int cz = record.z() >> 4;
                    if (!settings.loadChunks() && !world.isChunkLoaded(cx, cz)) {
                        skippedUnloaded.incrementAndGet();
                        continue;
                    }
                    if (settings.loadChunks() && !world.isChunkLoaded(cx, cz)) {
                        Chunk chunk = world.getChunkAt(cx, cz);
                        if (!chunk.isLoaded()) chunk.load();
                    }

                    Block block = world.getBlockAt(record.x(), record.y(), record.z());
                    BlockSnapshot expected = operation == RollbackOperation.ROLLBACK ? record.after() : record.before();
                    BlockSnapshot target = operation == RollbackOperation.ROLLBACK ? record.before() : record.after();

                    Material currentType = block.getType();
                    String currentData = block.getBlockData().getAsString();
                    if (!expected.matches(currentType, currentData)) {
                        skipped.incrementAndGet();
                        continue;
                    }

                    if (applySnapshot(block, target)) {
                        changed.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                }

                if (!queue.isEmpty()) return;
                cancel();

                sender.sendMessage(GradientText.prefix() +
                        GradientText.gradient(operation.name().toLowerCase(Locale.ROOT)) +
                        GradientText.gray() + " complete. " +
                        GradientText.yellow() + "changed=" + changed.get() + GradientText.gray() + " " +
                        GradientText.yellow() + "skipped=" + skipped.get() + GradientText.gray() + " " +
                        GradientText.yellow() + "unloaded=" + skippedUnloaded.get() + GradientText.gray() + " " +
                        GradientText.yellow() + "failed=" + failed.get() +
                        GradientText.reset());
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static boolean applySnapshot(Block block, BlockSnapshot snapshot) {
        Material material = materialFromKey(snapshot.materialKey());
        if (material == null) return false;

        String data = snapshot.blockData();
        if (data == null || data.isBlank()) {
            block.setType(material, false);
            return true;
        }

        try {
            BlockData blockData = Bukkit.createBlockData(data);
            block.setBlockData(blockData, false);
            return true;
        } catch (IllegalArgumentException e) {
            block.setType(material, false);
            return true;
        }
    }

    private static Material materialFromKey(String key) {
        if (key == null || key.isBlank()) return null;
        Material byKey = Material.matchMaterial(key);
        if (byKey != null) return byKey;
        int colon = key.indexOf(':');
        if (colon > 0 && colon + 1 < key.length()) {
            return Material.matchMaterial(key.substring(colon + 1).toUpperCase(Locale.ROOT));
        }
        return Material.matchMaterial(key.toUpperCase(Locale.ROOT));
    }

    private <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}
