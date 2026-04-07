package nl.amk.blocklookup.db.model;

import java.util.UUID;

public record ChatEventRecord(
        long id,
        long timeMs,
        UUID playerUuid,
        String playerName,
        String message
) {
}

