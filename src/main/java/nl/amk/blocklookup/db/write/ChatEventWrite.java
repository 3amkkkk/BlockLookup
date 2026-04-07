package nl.amk.blocklookup.db.write;

import java.util.UUID;

public record ChatEventWrite(
        long timeMs,
        UUID playerUuid,
        String playerName,
        String message
) implements DbWrite {
}

