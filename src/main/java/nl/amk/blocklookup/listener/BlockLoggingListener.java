package nl.amk.blocklookup.listener;

import nl.amk.blocklookup.db.DatabaseWriter;
import nl.amk.blocklookup.db.model.BlockSnapshot;
import nl.amk.blocklookup.db.write.BlockEventWrite;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockLoggingListener implements Listener {

    private final DatabaseWriter writer;

    public BlockLoggingListener(DatabaseWriter writer) {
        this.writer = writer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        BlockState before = event.getBlockReplacedState();
        BlockState after = event.getBlockPlaced().getState();

        writer.enqueue(new BlockEventWrite(
                System.currentTimeMillis(),
                player.getUniqueId(),
                player.getName(),
                event.getBlock().getWorld().getUID(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ(),
                "PLACE",
                BlockSnapshot.from(before),
                BlockSnapshot.from(after)
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BlockState before = event.getBlock().getState();

        writer.enqueue(new BlockEventWrite(
                System.currentTimeMillis(),
                player.getUniqueId(),
                player.getName(),
                event.getBlock().getWorld().getUID(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ(),
                "BREAK",
                BlockSnapshot.from(before),
                BlockSnapshot.air()
        ));
    }
}

