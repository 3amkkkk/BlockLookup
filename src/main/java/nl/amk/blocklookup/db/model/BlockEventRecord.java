package nl.amk.blocklookup.db.model;

import java.util.UUID;

public record BlockEventRecord(
        long id,
        long timeMs,
        UUID playerUuid,
        String playerName,
        UUID worldUuid,
        int x,
        int y,
        int z,
        String action,
        BlockSnapshot before,
        BlockSnapshot after
) {
}

