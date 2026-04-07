package nl.amk.blocklookup.db.write;

import nl.amk.blocklookup.db.model.BlockSnapshot;

import java.util.UUID;

public record BlockEventWrite(
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
) implements DbWrite {
}

