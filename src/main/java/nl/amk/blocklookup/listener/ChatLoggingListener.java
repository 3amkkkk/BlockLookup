package nl.amk.blocklookup.listener;

import nl.amk.blocklookup.db.DatabaseWriter;
import nl.amk.blocklookup.db.write.ChatEventWrite;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatLoggingListener implements Listener {

    private final DatabaseWriter writer;

    public ChatLoggingListener(DatabaseWriter writer) {
        this.writer = writer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message == null || message.isBlank()) return;

        writer.enqueue(new ChatEventWrite(
                System.currentTimeMillis(),
                player.getUniqueId(),
                player.getName(),
                message
        ));
    }
}
